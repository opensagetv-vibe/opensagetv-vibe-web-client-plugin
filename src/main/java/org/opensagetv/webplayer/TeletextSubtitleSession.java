package org.opensagetv.webplayer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-playback DVB Teletext Level-1 subtitle decoder.
 *
 * <p>This is a server-safe adaptation of the Vibe Android
 * {@code TeletextSubtitleEngine}: parser state is owned by one WebPlayer
 * playback instead of the Android process-global active session. It decodes
 * subtitle-page service types 2 and 5, PES data units, Hamming protected page
 * headers/rows, Level-1 spacing attributes, and erase-page updates. It is not
 * an interactive Teletext browser and does not claim Level 1.5/2.5 graphics or
 * complete national character-set support.</p>
 */
final class TeletextSubtitleSession {
    static final int TRACK_BASE=0x5400;
    private static final int MAX_PES_BYTES=256*1024;

    static final class Service {
        final int trackId,pid,type,page; final String language; final boolean hearingImpaired;
        Service(int trackId,int pid,String language,int type,int page){
            this.trackId=trackId;this.pid=pid;this.language=language==null?"":language.toLowerCase(Locale.ROOT);
            this.type=type;this.page=page;this.hearingImpaired=type==5;
        }
        String key(){return pid+":"+language+":"+type+":"+page;}
        String json(){return "{\"trackId\":"+trackId+",\"pid\":"+pid+",\"language\":\""+HttpUtil.json(language)+"\",\"type\":"+type+",\"page\":"+page+",\"hearingImpaired\":"+hearingImpaired+"}";}
    }

    private final PlaybackSessionContext context;
    private PlaybackSessionContext.Token token;
    private final Set<Integer> pmtPids=new LinkedHashSet<Integer>();
    private final Set<Integer> videoPids=new LinkedHashSet<Integer>();
    private final Set<Integer> teletextPids=new LinkedHashSet<Integer>();
    private final Map<Integer,SectionAssembler> sections=new LinkedHashMap<Integer,SectionAssembler>();
    private final Map<Integer,PesAssembler> pes=new LinkedHashMap<Integer,PesAssembler>();
    private final Map<String,Service> serviceByKey=new LinkedHashMap<String,Service>();
    private final Map<Integer,Set<String>> serviceKeysByPmt=new LinkedHashMap<Integer,Set<String>>();
    private final Map<Integer,PageState> pages=new LinkedHashMap<Integer,PageState>();
    private final SubtitleClock clock=new SubtitleClock();
    private byte[] carry=new byte[0];
    private int packetSize,nextTrackId=TRACK_BASE;
    private String lastSource="";
    private long expectedPosition=-1L;
    private int selectedTrack=-1,preferredPage=-1;
    private String preferredLanguage="";
    private boolean enabled;
    private long playbackAnchorMs,firstMediaPts=-1L;
    private long observeCalls,observedBytes,transportPackets,patSections,pmtSections,teletextDescriptors,teletextPesPackets,teletextDataUnits,emittedCues,clearCues,framingResets,continuityResets,duplicatePackets,malformedUnits,queueRejects;
    private final Map<Integer,Integer> continuity=new LinkedHashMap<Integer,Integer>();

    TeletextSubtitleSession(PlaybackSessionContext context,PlaybackSessionContext.Token token,long anchorMs){
        if(context==null||token==null)throw new IllegalArgumentException("playback context/token required");
        this.context=context;this.token=token;this.playbackAnchorMs=Math.max(0L,anchorMs);sections.put(Integer.valueOf(0),new SectionAssembler());
    }

    synchronized void seed(MediaProbe.Info info){
        if(info==null)return;
        boolean changed=false;
        for(MediaProbe.Track t:info.tracks){
            if(!"teletext".equals(t.serviceKind)||t.sourcePid<0||t.teletextPage<100)continue;
            int type=t.teletextType==5?5:2;
            if(addService(t.sourcePid,t.language,type,t.teletextPage))changed=true;
        }
        if(changed)resolveSelection();
    }

    synchronized void configure(boolean enabled,int preferredPage,String preferredLanguage){
        this.enabled=enabled;this.preferredPage=preferredPage;
        this.preferredLanguage=preferredLanguage==null?"":preferredLanguage.trim().toLowerCase(Locale.ROOT);
        if(!enabled){selectedTrack=-1;offerText(-1L,"",true);return;}
        resolveSelection();
    }

    /** Virtual CC1/CC2 Teletext resolver used by P03 and later P05 policy. */
    synchronized boolean selectVirtualSlot(int slot,String language){
        if(slot<1||slot>2)return false;
        List<Service> ordered=orderedServices(language);
        if(ordered.isEmpty())return false;
        Service selected=ordered.get(Math.min(slot-1,ordered.size()-1));
        return selectTrack(selected.trackId);
    }

    synchronized boolean selectService(int pid,int page,String language){
        for(Service s:serviceByKey.values())if((pid<0||s.pid==pid)&&(page<0||s.page==page)&&(language==null||language.isEmpty()||language.equalsIgnoreCase(s.language)))return selectTrack(s.trackId);
        return false;
    }

    synchronized boolean selectTrack(int trackId){
        if(!pages.containsKey(Integer.valueOf(trackId)))return false;
        selectedTrack=trackId;PageState p=pages.get(Integer.valueOf(trackId));
        offerPage(p,p==null?-1L:p.lastPts,true);return true;
    }

    synchronized void updateToken(PlaybackSessionContext.Token token,long anchorMs){
        if(token==null)return;this.token=token;this.playbackAnchorMs=Math.max(0L,anchorMs);clock.reset();resetFraming(true);resolveSelection();
    }

    synchronized void discontinuity(long anchorMs){playbackAnchorMs=Math.max(0L,anchorMs);clock.reset();resetFraming(true);resolveSelection();}

    synchronized void observe(String source,long absolutePosition,byte[] bytes,int offset,int length){
        if(bytes==null||length<=0||offset<0||offset>bytes.length-length)return;
        long begin=System.nanoTime();observeCalls++;observedBytes+=length;
        String safe=source==null?"":source;
        boolean sourceChanged=!lastSource.isEmpty()&&!lastSource.equals(safe);
        boolean jumped=absolutePosition>=0&&expectedPosition>=0&&absolutePosition!=expectedPosition;
        if(sourceChanged||jumped)resetFraming(true);
        lastSource=safe;expectedPosition=absolutePosition<0?-1:absolutePosition+length;
        byte[] combined=new byte[carry.length+length];System.arraycopy(carry,0,combined,0,carry.length);System.arraycopy(bytes,offset,combined,carry.length,length);parse(combined);
        if(System.nanoTime()-begin>SubtitleLimits.MAX_DECODE_SLICE_NANOS){/* diagnostic only; caller remains bounded by input chunks */}
    }

    synchronized Service[] services(){return serviceByKey.values().toArray(new Service[serviceByKey.size()]);}
    synchronized int selectedTrack(){return selectedTrack;}
    synchronized boolean enabled(){return enabled;}
    synchronized String servicesJson(){StringBuilder b=new StringBuilder("[");int i=0;for(Service s:serviceByKey.values()){if(i++>0)b.append(',');b.append(s.json());}return b.append(']').toString();}
    synchronized String stateJson(){return "{\"active\":"+enabled+",\"selectedTrack\":"+selectedTrack+",\"services\":"+servicesJson()+"}";}
    synchronized String diagnosticsJson(){
        String stage=emittedCues>0?"decoded-presentation":teletextDataUnits>0?"data-units":teletextPesPackets>0?"pes":teletextDescriptors>0?"descriptor-only":"no-descriptor";
        return "{\"active\":"+enabled+",\"stage\":\""+stage+"\",\"selectedTrack\":"+selectedTrack+",\"preferredPage\":"+preferredPage+",\"preferredLanguage\":\""+HttpUtil.json(preferredLanguage)+"\",\"calls\":"+observeCalls+",\"bytes\":"+observedBytes+",\"tsPackets\":"+transportPackets+",\"pat\":"+patSections+",\"pmt\":"+pmtSections+",\"descriptors\":"+teletextDescriptors+",\"services\":"+serviceByKey.size()+",\"pes\":"+teletextPesPackets+",\"units\":"+teletextDataUnits+",\"cues\":"+emittedCues+",\"clearCues\":"+clearCues+",\"framingResets\":"+framingResets+",\"continuityResets\":"+continuityResets+",\"duplicatePackets\":"+duplicatePackets+",\"malformedUnits\":"+malformedUnits+",\"queueRejects\":"+queueRejects+",\"serviceInventory\":"+servicesJson()+"}";
    }

    private void resolveSelection(){
        if(!enabled){selectedTrack=-1;return;}
        Service best=null;
        for(Service s:serviceByKey.values()){
            boolean pageOk=preferredPage<100||s.page==preferredPage;
            boolean langOk=preferredLanguage.isEmpty()||preferredLanguage.equalsIgnoreCase(s.language)||preferredLanguage.substring(0,Math.min(2,preferredLanguage.length())).equalsIgnoreCase(s.language.substring(0,Math.min(2,s.language.length())));
            if(pageOk&&langOk){best=s;break;}
            if(best==null&&pageOk)best=s;
        }
        if(best==null&&!serviceByKey.isEmpty())best=orderedServices(preferredLanguage).get(0);
        if(best!=null&&selectedTrack!=best.trackId){selectedTrack=best.trackId;PageState p=pages.get(Integer.valueOf(best.trackId));offerPage(p,p==null?-1L:p.lastPts,true);}
    }

    private List<Service> orderedServices(String language){
        List<Service> out=new ArrayList<Service>(serviceByKey.values());final String want=language==null?"":language.toLowerCase(Locale.ROOT);
        java.util.Collections.sort(out,(a,b)->{int ae=languageScore(a.language,want),be=languageScore(b.language,want);if(ae!=be)return ae-be;if(a.hearingImpaired!=b.hearingImpaired)return a.hearingImpaired?1:-1;return Integer.compare(a.page,b.page);});return out;
    }
    private static int languageScore(String actual,String wanted){if(wanted.isEmpty())return actual.startsWith("en")?0:1;if(actual.equals(wanted))return 0;if(actual.length()>=2&&wanted.length()>=2&&actual.substring(0,2).equals(wanted.substring(0,2)))return 1;return 2;}

    private boolean addService(int pid,String language,int type,int page){
        teletextPids.add(Integer.valueOf(pid));String key=serviceKey(pid,language,type,page);
        if(serviceByKey.containsKey(key))return false;
        if(serviceByKey.size()>=SubtitleLimits.MAX_DISCOVERED_SERVICES)return false;
        Service service=new Service(nextTrackId++,pid,language,type,page);serviceByKey.put(key,service);pages.put(Integer.valueOf(service.trackId),new PageState(service));return true;
    }


    private static String serviceKey(int pid,String language,int type,int page){return pid+":"+(language==null?"":language.toLowerCase(Locale.ROOT))+":"+type+":"+page;}
    private void rebuildTeletextPidSet(){teletextPids.clear();for(Service s:serviceByKey.values())teletextPids.add(Integer.valueOf(s.pid));}

    private void resetFraming(boolean generation){
        framingResets++;carry=new byte[0];packetSize=0;for(SectionAssembler a:sections.values())a.reset();for(PesAssembler a:pes.values())a.reset();continuity.clear();firstMediaPts=-1L;
        if(generation){for(PageState p:pages.values())p.clear();offerText(-1L,"",true);}
    }

    private void parse(byte[] bytes){
        int pos=0;if(packetSize==0){int[] sync=findSync(bytes,0);if(sync==null){carry=tail(bytes,816);return;}pos=sync[0];packetSize=sync[1];}
        while(pos+packetSize<=bytes.length){int syncPos=pos;if(packetSize==192)syncPos=pos+4;if((bytes[syncPos]&255)!=0x47){packetSize=0;int[] sync=findSync(bytes,pos+1);if(sync==null){carry=tail(bytes,816);return;}pos=sync[0];packetSize=sync[1];continue;}parsePacket(bytes,syncPos);pos+=packetSize;}carry=Arrays.copyOfRange(bytes,pos,bytes.length);
    }

    private void parsePacket(byte[] bytes,int start){
        transportPackets++;if(start+188>bytes.length)return;int b1=bytes[start+1]&255;if((b1&0x80)!=0)return;boolean payloadStart=(b1&0x40)!=0;int pid=((b1&31)<<8)|(bytes[start+2]&255);int cc=bytes[start+3]&15;int control=(bytes[start+3]>>4)&3;
        if(control==1||control==3){Integer prior=continuity.get(Integer.valueOf(pid));if(prior!=null){int expected=(prior+1)&15;if(cc==prior&&!payloadStart){duplicatePackets++;return;}if(cc!=expected){continuityResets++;PesAssembler pa=pes.get(Integer.valueOf(pid));if(pa!=null)pa.reset();SectionAssembler sa=sections.get(Integer.valueOf(pid));if(sa!=null)sa.reset();}}continuity.put(Integer.valueOf(pid),Integer.valueOf(cc));}
        if(control!=1&&control!=3)return;int payload=start+4;if(control==3){if(payload>=start+188)return;int al=bytes[payload]&255;payload+=1+al;}int end=start+188;if(payload>=end)return;
        if(pid==0||pmtPids.contains(Integer.valueOf(pid))){SectionAssembler a=sections.get(Integer.valueOf(pid));if(a==null){a=new SectionAssembler();sections.put(Integer.valueOf(pid),a);}for(byte[] sec:a.consume(bytes,payload,end,payloadStart))parseSection(pid,sec);}
        if(videoPids.contains(Integer.valueOf(pid))&&payloadStart){long pts=pesPts(bytes,payload,end);if(pts>=0&&firstMediaPts<0){firstMediaPts=pts;clock.anchor(pts,playbackAnchorMs);}}
        if(teletextPids.contains(Integer.valueOf(pid))){PesAssembler a=pes.get(Integer.valueOf(pid));if(a==null){a=new PesAssembler(pid);pes.put(Integer.valueOf(pid),a);}a.consume(bytes,payload,end,payloadStart);}
    }

    private void parseSection(int pid,byte[] section){
        if(section.length<8)return;int table=section[0]&255;
        if(pid==0&&table==0){patSections++;for(int p=8;p+4<=section.length-4;p+=4){int program=((section[p]&255)<<8)|(section[p+1]&255);if(program==0)continue;int pmt=((section[p+2]&31)<<8)|(section[p+3]&255);pmtPids.add(Integer.valueOf(pmt));if(!sections.containsKey(Integer.valueOf(pmt)))sections.put(Integer.valueOf(pmt),new SectionAssembler());}}
        else if(table==2){pmtSections++;parsePmt(pid,section);}
    }

    private void parsePmt(int pmtPid,byte[] section){
        if(section.length<16)return;int p=12+(((section[10]&15)<<8)|(section[11]&255)),end=section.length-4;boolean changed=false;Set<String> currentKeys=new LinkedHashSet<String>();
        while(p+5<=end){int type=section[p]&255,pid=((section[p+1]&31)<<8)|(section[p+2]&255),info=((section[p+3]&15)<<8)|(section[p+4]&255);if(isVideoType(type))videoPids.add(Integer.valueOf(pid));int d=p+5,dEnd=Math.min(end,d+info);while(d+2<=dEnd){int tag=section[d]&255,len=section[d+1]&255,v=d+2;if(v+len>dEnd)break;if(type==6&&tag==0x56){for(int q=v;q+5<=v+len;q+=5){teletextDescriptors++;String lang=ascii(section,q,3);int tm=section[q+3]&255,t=(tm>>3)&31;if(t==2||t==5){int mag=tm&7;if(mag==0)mag=8;int bcd=section[q+4]&255,page=mag*100+((bcd>>4)&15)*10+(bcd&15);String key=serviceKey(pid,lang,t,page);currentKeys.add(key);if(addService(pid,lang,t,page))changed=true;}}}d=v+len;}p=dEnd;}
        Set<String> prior=serviceKeysByPmt.put(Integer.valueOf(pmtPid),currentKeys);
        if(prior!=null){for(String key:prior)if(!currentKeys.contains(key)){Service old=serviceByKey.remove(key);if(old!=null){pages.remove(Integer.valueOf(old.trackId));if(selectedTrack==old.trackId)selectedTrack=-1;changed=true;}}}
        if(changed){rebuildTeletextPidSet();resolveSelection();}
    }

    private void parsePes(int pid,byte[] data,int length){
        if(length<10||data[0]!=0||data[1]!=0||data[2]!=1)return;teletextPesPackets++;int header=data[8]&255,payload=9+header;if(payload>=length)return;long pts=(data[7]&0x80)!=0?parsePts(data,9,length):-1L;
        if(firstMediaPts<0&&pts>=0){firstMediaPts=pts;clock.anchor(pts,playbackAnchorMs);}int id=data[payload]&255;if((id>=0x10&&id<=0x1f)||(id>=0x99&&id<=0x9b))payload++;
        Set<Integer> changed=new LinkedHashSet<Integer>();while(payload+2<=length){int unit=data[payload]&255,unitLength=data[payload+1]&255,v=payload+2;if(v+unitLength>length){malformedUnits++;break;}if((unit==2||unit==3)&&unitLength==44){teletextDataUnits++;decodeUnit(pid,data,v,pts,changed);}payload=v+unitLength;}
        for(Integer track:changed){PageState page=pages.get(track);if(page!=null)offerPage(page,pts,false);}
    }

    private void decodeUnit(int pid,byte[] data,int start,long pts,Set<Integer> changed){
        if(start+44>data.length){malformedUnits++;return;}int a=unham(data[start+2]&255),b=unham(data[start+3]&255);if(a<0||b<0)return;int address=reverse8((a<<4)|b),mag=(address&7)==0?8:address&7,row=address>>3;
        if(row==0){int ones=unham(data[start+4]&255),tens=unham(data[start+5]&255);if(ones<0||tens<0)return;ones=reverse4(ones);tens=reverse4(tens);int pageNum=mag*100+tens*10+ones;for(PageState page:pages.values()){if(page.service.pid!=pid||page.service.page/100!=mag)continue;page.currentPage=pageNum;if(pageNum==page.service.page){int c4=unham(data[start+7]&255);if(c4>=0)c4=reverse4(c4);if(c4>=0&&(c4&8)!=0)page.clearRows();page.lastPts=pts;changed.add(Integer.valueOf(page.service.trackId));}}return;}
        if(row<1||row>23)return;for(PageState page:pages.values()){if(page.service.pid!=pid||page.currentPage!=page.service.page)continue;String line=decodeText(data,start+4,40);if(!line.equals(page.rows[row])){page.rows[row]=line;page.lastPts=pts;changed.add(Integer.valueOf(page.service.trackId));}}
    }

    private void offerPage(PageState page,long pts,boolean immediate){if(page==null||page.service.trackId!=selectedTrack||!enabled)return;offerText(immediate?-1L:pts,page.text(),page.text().isEmpty());}
    private void offerText(long pts,String text,boolean clear){
        if(!enabled&&!(clear&&selectedTrack<0))return;long position=playbackAnchorMs;if(pts>=0){position=clock.sourcePtsToRecordingMs(pts);}String safe=text==null?"":text;SubtitleCue cue=SubtitleCue.text(token,SubtitleCue.Owner.LOCAL_BROADCAST,position,0,safe,clear||safe.isEmpty(),false);if(context.cues().offer(cue)){emittedCues++;if(cue.clear)clearCues++;}else queueRejects++;
    }

    private final class PesAssembler {
        final int pid;byte[] bytes=new byte[4096];int length;PesAssembler(int pid){this.pid=pid;}
        void consume(byte[] source,int start,int end,boolean payloadStart){if(payloadStart){finish();length=0;}int amount=end-start;if(amount<=0||length+amount>MAX_PES_BYTES){if(length+amount>MAX_PES_BYTES)reset();return;}if(length+amount>bytes.length)bytes=Arrays.copyOf(bytes,Math.min(MAX_PES_BYTES,Math.max(length+amount,bytes.length*2)));System.arraycopy(source,start,bytes,length,amount);length+=amount;int declared=declaredPesLength(bytes,length);if(declared>0&&length>=declared)finish();}
        void finish(){if(length>0)parsePes(pid,bytes,length);length=0;}void reset(){length=0;}
    }

    private static final class PageState {
        final Service service;final String[] rows=new String[24];int currentPage=-1;long lastPts=-1L;PageState(Service service){this.service=service;clearRows();}
        void clear(){currentPage=-1;lastPts=-1;clearRows();}void clearRows(){Arrays.fill(rows,"");}
        String text(){StringBuilder b=new StringBuilder();for(int r=1;r<=23;r++){if(rows[r].isEmpty())continue;if(b.length()>0)b.append('\n');b.append(rows[r]);}return b.toString();}
    }

    private static final class SectionAssembler {
        byte[] section=new byte[4096];int length,expected=-1;List<byte[]> consume(byte[] source,int start,int end,boolean payloadStart){List<byte[]> result=new ArrayList<byte[]>();int p=start;if(payloadStart){if(p>=end)return result;int pointer=source[p++]&255,oldEnd=Math.min(end,p+pointer);if(length>0)append(source,p,oldEnd,result);p=oldEnd;if(length>0)reset();}append(source,p,end,result);return result;}
        void append(byte[] source,int start,int end,List<byte[]> result){for(int p=start;p<end;p++){if(length==0&&(source[p]&255)==0xff)return;if(length>=section.length){reset();return;}section[length++]=source[p];if(length==3){expected=3+(((section[1]&15)<<8)|(section[2]&255));if(expected<3||expected>section.length){reset();return;}}if(expected>0&&length==expected){result.add(Arrays.copyOf(section,length));reset();}}}void reset(){length=0;expected=-1;}
    }

    private static final int[] HAMMING={0xA8,0x0B,0x26,0x85,0x92,0x31,0x1C,0xBF,0x40,0xE3,0xCE,0x6D,0x7A,0xD9,0xF4,0x57};
    private static int unham(int value){int best=-1,distance=9;for(int n=0;n<HAMMING.length;n++){int d=Integer.bitCount((value&255)^HAMMING[n]);if(d<distance){distance=d;best=n;}}return distance<=1?best:-1;}
    private static String decodeText(byte[] b,int start,int length){StringBuilder r=new StringBuilder(length);for(int i=0;i<length;i++){int v=reverse8(b[start+i]&255)&0x7f;r.append(v>=0x20&&v<0x7f?(char)v:' ');}int first=0,last=r.length();while(first<last&&r.charAt(first)==' ')first++;while(last>first&&r.charAt(last-1)==' ')last--;return r.substring(first,last);}
    private static int reverse8(int v){v=((v>>>1)&0x55)|((v<<1)&0xaa);v=((v>>>2)&0x33)|((v<<2)&0xcc);return (((v>>>4)&15)|((v<<4)&0xf0))&255;}
    private static int reverse4(int v){return ((v&1)<<3)|((v&2)<<1)|((v&4)>>>1)|((v&8)>>>3);}
    private static int[] findSync(byte[] b,int start){int[] sizes={188,192,204};for(int p=Math.max(0,start);p<b.length;p++){for(int size:sizes){int syncOffset=size==192?4:0;int s=p+syncOffset;if(s>=b.length||(b[s]&255)!=0x47)continue;if(s+size*3<b.length&&(b[s+size]&255)==0x47&&(b[s+size*2]&255)==0x47&&(b[s+size*3]&255)==0x47)return new int[]{p,size};}}return null;}
    private static byte[] tail(byte[] b,int max){int n=Math.min(b.length,max);return Arrays.copyOfRange(b,b.length-n,b.length);}
    private static boolean isVideoType(int t){return t==1||t==2||t==0x10||t==0x1b||t==0x20||t==0x24||t==0xea;}
    private static int declaredPesLength(byte[] b,int length){if(length<6||b[0]!=0||b[1]!=0||b[2]!=1)return -1;int payload=((b[4]&255)<<8)|(b[5]&255);return payload==0?-1:payload+6;}
    private static long pesPts(byte[] b,int start,int end){if(start+14>end||b[start]!=0||b[start+1]!=0||b[start+2]!=1||(b[start+7]&0x80)==0)return -1;return parsePts(b,start+9,end);}
    private static long parsePts(byte[] b,int p,int end){if(p<0||p+5>end)return -1;return ((long)(b[p]&0x0e)<<29)|((long)(b[p+1]&255)<<22)|((long)(b[p+2]&0xfe)<<14)|((long)(b[p+3]&255)<<7)|((long)(b[p+4]&0xfe)>>1);}
    private static String ascii(byte[] b,int start,int length){StringBuilder t=new StringBuilder(length);for(int i=0;i<length;i++){int v=b[start+i]&255;t.append(v>=32&&v<=126?(char)v:'?');}return t.toString();}
}
