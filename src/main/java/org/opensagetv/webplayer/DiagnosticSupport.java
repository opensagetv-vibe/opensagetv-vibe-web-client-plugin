package org.opensagetv.webplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** P14 bounded diagnostic bundle + administrator-configured mounted-share support. */
final class DiagnosticSupport {
    static final int MAX_BROWSER_JSON=256*1024;
    static final long MAX_SPOOL_BYTES=10L*1024L*1024L;
    static final int MAX_SPOOL_FILES=10;
    private static final Pattern SECRET=Pattern.compile("(?i)\\\"(authorization|cookie|password|passwd|secret|token|credential|session.?key|crypto[^\\\"]*|private.?key|share.?user|share.?pass)\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|[^,}\\r\\n]+)");
    private static final Pattern CONTENT=Pattern.compile("(?i)\\\"(payload|subtitle.?text|caption.?text|media.?bytes|raw.?data|cue.?data)\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|\\[[^]]*]|[^,}\\r\\n]+)");
    private static final DateTimeFormatter TS=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final ConcurrentHashMap<String,AtomicBoolean> OPERATIONS=new ConcurrentHashMap<String,AtomicBoolean>();

    static final class SaveResult {
        final String state,detail,fileName; SaveResult(String s,String d,String f){state=s;detail=d;fileName=f;}
        String json(){return "{\"state\":\""+HttpUtil.json(state)+"\",\"detail\":\""+HttpUtil.json(detail)+"\",\"fileName\":\""+HttpUtil.json(fileName)+"\"}";}
    }
    static final class TestResult {
        final boolean ok; final String stage,detail; TestResult(boolean ok,String stage,String detail){this.ok=ok;this.stage=stage;this.detail=detail;}
        String json(){return "{\"ok\":"+ok+",\"stage\":\""+HttpUtil.json(stage)+"\",\"detail\":\""+HttpUtil.json(detail)+"\"}";}
    }
    static String sanitize(String raw){
        if(raw==null)return "null"; if(raw.length()>MAX_BROWSER_JSON)raw=raw.substring(0,MAX_BROWSER_JSON);
        raw=replace(SECRET,raw,"[redacted]"); raw=replace(CONTENT,raw,"[omitted-content]"); return raw;
    }
    private static String replace(Pattern p,String in,String value){Matcher m=p.matcher(in);StringBuffer b=new StringBuffer();while(m.find())m.appendReplacement(b,Matcher.quoteReplacement("\""+m.group(1)+"\":\""+value+"\""));m.appendTail(b);return b.toString();}
    static byte[] zip(String browser,String mini,String hls,String transcoder,String recentTest)throws IOException{
        String report="{\n  \"schemaVersion\":1,\n  \"generatedAt\":\""+HttpUtil.json(Instant.now().toString())+"\",\n  \"version\":\""+HttpUtil.json(PluginVersion.VERSION)+"\",\n  \"browser\":"+sanitize(browser)+",\n  \"miniClient\":"+sanitize(mini)+",\n  \"stream\":"+sanitize(hls)+",\n  \"transcoder\":"+sanitize(transcoder)+",\n  \"recentVideoTest\":"+sanitize(recentTest)+"\n}\n";
        byte[] data=report.getBytes(StandardCharsets.UTF_8);String hash=sha256(data);
        ByteArrayOutputStream raw=new ByteArrayOutputStream(Math.min(1024*1024,data.length+2048));
        try(ZipOutputStream z=new ZipOutputStream(raw,StandardCharsets.UTF_8)){
            entry(z,"report.json",data);
            entry(z,"hashes.sha256",(hash+"  report.json\n").getBytes(StandardCharsets.UTF_8));
            entry(z,"README.txt",("SageTV WebPlayer diagnostic bundle\nGenerated UTC: "+Instant.now()+"\nContent/media payloads and recognized credential fields are omitted or redacted.\nServer share writes are disabled unless an administrator enables sagetv.webplayer.diagnostics.writeEnabled and configures sagetv.webplayer.diagnostics.dir.\n").getBytes(StandardCharsets.UTF_8));
        }
        return raw.toByteArray();
    }
    private static void entry(ZipOutputStream z,String name,byte[] data)throws IOException{ZipEntry e=new ZipEntry(name);e.setTime(0);z.putNextEntry(e);z.write(data);z.closeEntry();}
    static String transcoderJson(){TranscoderManager.Probe p=TranscoderManager.probe();return "{\"available\":"+p.available+",\"executable\":\""+HttpUtil.json(p.executable)+"\",\"detail\":\""+HttpUtil.json(p.detail)+"\",\"source\":\""+HttpUtil.json(p.source)+"\",\"videoEncoder\":\""+HttpUtil.json(p.videoEncoder)+"\",\"hardware\":"+p.hardware+",\"hardwareDecode\":"+p.hardwareDecode+",\"hardwareFilters\":"+p.hardwareFilters+",\"decodeAccelerator\":\""+HttpUtil.json(p.decodeAccelerator)+"\",\"accelerator\":\""+HttpUtil.json(p.accelerator)+"\"}";}
    static boolean writesEnabled(){return Boolean.parseBoolean(System.getProperty("sagetv.webplayer.diagnostics.writeEnabled","false"));}
    static Path destination(){String s=System.getProperty("sagetv.webplayer.diagnostics.dir","").trim();return s.isEmpty()?null:Paths.get(s).toAbsolutePath().normalize();}
    static Path spoolDir(){return Paths.get(System.getProperty("java.io.tmpdir","."),"sagetv-webplayer","diagnostic-spool").toAbsolutePath().normalize();}
    static String statusJson(){Path d=destination();long count=0,bytes=0;try{for(Path p:listSpool()){count++;bytes+=Files.size(p);}}catch(Exception ignored){}String name=d==null?"":String.valueOf(d.getFileName());return "{\"configured\":"+(d!=null)+",\"writesEnabled\":"+writesEnabled()+",\"destinationName\":\""+HttpUtil.json(name)+"\",\"spoolFiles\":"+count+",\"spoolBytes\":"+bytes+",\"policy\":\"mounted server path only; browser receives no SMB credentials\"}";}
    static AtomicBoolean beginOperation(String id){String key=operationId(id);AtomicBoolean created=new AtomicBoolean(false),prior=OPERATIONS.putIfAbsent(key,created);return prior==null?created:prior;}
    static boolean cancelOperation(String id){AtomicBoolean flag=OPERATIONS.get(operationId(id));if(flag==null)return false;flag.set(true);return true;}
    static void endOperation(String id,AtomicBoolean flag){OPERATIONS.remove(operationId(id),flag);}
    private static String operationId(String id){String s=id==null?"":id.replaceAll("[^A-Za-z0-9_-]","");return s.isEmpty()?"anonymous":s.substring(0,Math.min(80,s.length()));}
    static TestResult testDestination(){return testDestination(new AtomicBoolean(false));}
    static TestResult testDestination(AtomicBoolean cancel){Path d=destination();if(!writesEnabled())return new TestResult(false,"policy","Server diagnostic writes are disabled");if(d==null)return new TestResult(false,"configuration","No diagnostic destination is configured");if(cancelled(cancel))return new TestResult(false,"cancelled","Cancelled before the destination test started");byte[] data=("SageTV WebPlayer diagnostic path test "+Instant.now()).getBytes(StandardCharsets.UTF_8);Path f=null;try{Files.createDirectories(d);if(cancelled(cancel))return new TestResult(false,"cancelled","Cancelled before temporary-file creation");f=d.resolve(".sagetv-webplayer-test-"+UUID.randomUUID().toString().replace("-","") + ".tmp");Files.write(f,data,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);if(cancelled(cancel))return new TestResult(false,"cancelled","Cancelled after the bounded write");byte[] read=Files.readAllBytes(f);if(cancelled(cancel))return new TestResult(false,"cancelled","Cancelled after the bounded read");if(!sha256(data).equals(sha256(read)))return new TestResult(false,"hash","Write/read hash mismatch");Files.delete(f);return new TestResult(true,"complete","Bounded write/read/hash/delete succeeded");}catch(Exception e){return new TestResult(false,"io",compact(e));}finally{if(f!=null)try{Files.deleteIfExists(f);}catch(Exception ignored){}}}
    static SaveResult save(byte[] zip){String name="SageTV-WebPlayer-diagnostics-"+TS.format(Instant.now())+".zip";Path d=destination();if(writesEnabled()&&d!=null){try{atomicWrite(d,name,zip);return new SaveResult("external","Saved to configured server diagnostic destination",name);}catch(Exception e){try{spool(name,zip);return new SaveResult("spooled","Destination failed; retained in bounded local spool: "+compact(e),name);}catch(Exception x){return new SaveResult("failed","Destination and spool failed: "+compact(x),name);}}}return new SaveResult("not-requested","External diagnostic writes are disabled or unconfigured",name);}
    static SaveResult retrySpool(){return retrySpool(new AtomicBoolean(false));}
    static SaveResult retrySpool(AtomicBoolean cancel){if(!writesEnabled()||destination()==null)return new SaveResult("disabled","Configure and enable server diagnostic writes first","");int moved=0;String last="";for(Path p:listSpool()){if(cancelled(cancel))return new SaveResult("cancelled","Cancelled after moving "+moved+" spooled report(s)","");try{byte[] data=Files.readAllBytes(p);if(cancelled(cancel))return new SaveResult("cancelled","Cancelled after moving "+moved+" spooled report(s)","");atomicWrite(destination(),p.getFileName().toString(),data);Files.deleteIfExists(p);moved++;}catch(Exception e){last=compact(e);break;}}return new SaveResult(last.isEmpty()?"retried":"partial","Moved "+moved+" spooled report(s)"+(last.isEmpty()?"":"; stopped: "+last),"");}
    private static boolean cancelled(AtomicBoolean flag){return flag!=null&&flag.get();}
    private static void atomicWrite(Path dir,String name,byte[] data)throws IOException{Files.createDirectories(dir);Path tmp=Files.createTempFile(dir,".sagetv-webplayer-",".tmp");try{Files.write(tmp,data,StandardOpenOption.TRUNCATE_EXISTING);Path target=dir.resolve(name);try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target);} }finally{Files.deleteIfExists(tmp);}}
    private static void spool(String name,byte[] data)throws IOException{Path d=spoolDir();Files.createDirectories(d);pruneSpool(data.length);atomicWrite(d,name,data);pruneSpool(0);}
    private static List<Path> listSpool(){Path d=spoolDir();if(!Files.isDirectory(d))return Collections.emptyList();List<Path> out=new ArrayList<Path>();try(DirectoryStream<Path> ds=Files.newDirectoryStream(d,"*.zip")){for(Path p:ds)if(Files.isRegularFile(p))out.add(p);}catch(IOException ignored){}Collections.sort(out,new Comparator<Path>(){public int compare(Path a,Path b){try{return Long.compare(Files.getLastModifiedTime(a).toMillis(),Files.getLastModifiedTime(b).toMillis());}catch(IOException e){return a.toString().compareTo(b.toString());}}});return out;}
    private static void pruneSpool(long incoming)throws IOException{List<Path> files=listSpool();long bytes=incoming;for(Path p:files)bytes+=Files.size(p);while(!files.isEmpty()&&(files.size()+(incoming>0?1:0)>MAX_SPOOL_FILES||bytes>MAX_SPOOL_BYTES)){Path p=files.remove(0);bytes-=Files.size(p);Files.deleteIfExists(p);}}
    static String sha256(byte[] data){try{MessageDigest m=MessageDigest.getInstance("SHA-256");byte[] d=m.digest(data);StringBuilder b=new StringBuilder(64);for(byte x:d)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static String compact(Exception e){
        String s=e.getMessage();if(s==null)s=e.getClass().getSimpleName();s=s.replace('\r',' ').replace('\n',' ').trim();
        Path d=destination();if(d!=null)s=redactPath(s,d,"[configured-destination]");
        s=redactPath(s,spoolDir(),"[diagnostic-spool]");
        return s;
    }
    private static String redactPath(String text,Path path,String replacement){
        if(text==null||text.isEmpty()||path==null)return text;
        String full=path.toString();if(!full.isEmpty())text=text.replace(full,replacement);
        try{String real=path.toFile().getCanonicalPath();if(!real.isEmpty())text=text.replace(real,replacement);}catch(IOException ignored){}
        return text;
    }
    private DiagnosticSupport(){}
}
