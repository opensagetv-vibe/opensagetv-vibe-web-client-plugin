package org.opensagetv.webplayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java DVB bitmap subtitle decoder (ETSI EN 300 743 display sets).
 *
 * <p>The parsing/drawing model follows the Apache-2.0 AndroidX Media3 DVB
 * parser, but replaces Android Bitmap/Canvas/SparseArray with bounded Java
 * arrays and straight-alpha RGBA rectangles suitable for the browser cue
 * contract. State is owned by one DVB service; no Android/JNI runtime is
 * required.</p>
 */
final class DvbBitmapDecoder {
    static final class BitmapRect {
        final int canvasWidth,canvasHeight,x,y,width,height; final byte[] rgba;
        BitmapRect(int cw,int ch,int x,int y,int w,int h,byte[] rgba){this.canvasWidth=cw;this.canvasHeight=ch;this.x=x;this.y=y;this.width=w;this.height=h;this.rgba=rgba;}
    }
    static final class Frame {
        final List<BitmapRect> rects; final int timeoutSeconds; final int canvasWidth,canvasHeight;
        Frame(List<BitmapRect> r,int timeout,int cw,int ch){rects=r;timeoutSeconds=timeout;canvasWidth=cw;canvasHeight=ch;}
        boolean clear(){return rects.isEmpty();}
    }

    private static final int PAGE_STATE_NORMAL=0;
    private static final byte[] DEFAULT_MAP_2_TO_4={0x00,0x07,0x08,0x0f};
    private static final byte[] DEFAULT_MAP_2_TO_8={0x00,0x77,(byte)0x88,(byte)0xff};
    private static final byte[] DEFAULT_MAP_4_TO_8={0x00,0x11,0x22,0x33,0x44,0x55,0x66,0x77,(byte)0x88,(byte)0x99,(byte)0xaa,(byte)0xbb,(byte)0xcc,(byte)0xdd,(byte)0xee,(byte)0xff};

    private final int compositionPageId,ancillaryPageId;
    private DisplayDefinition display=new DisplayDefinition(719,575,0,719,0,575);
    private PageComposition page;
    private final Map<Integer,RegionComposition> regions=new LinkedHashMap<Integer,RegionComposition>();
    private final Map<Integer,ClutDefinition> cluts=new LinkedHashMap<Integer,ClutDefinition>();
    private final Map<Integer,ClutDefinition> ancillaryCluts=new LinkedHashMap<Integer,ClutDefinition>();
    private final Map<Integer,ObjectData> objects=new LinkedHashMap<Integer,ObjectData>();
    private final Map<Integer,ObjectData> ancillaryObjects=new LinkedHashMap<Integer,ObjectData>();
    private long malformedSegments,renderedFrames;

    DvbBitmapDecoder(int compositionPageId,int ancillaryPageId){this.compositionPageId=compositionPageId;this.ancillaryPageId=ancillaryPageId;}

    boolean acceptsPage(int pageId){return pageId==compositionPageId||pageId==ancillaryPageId;}
    long malformedSegments(){return malformedSegments;}
    long renderedFrames(){return renderedFrames;}

    void reset(){page=null;regions.clear();cluts.clear();ancillaryCluts.clear();objects.clear();ancillaryObjects.clear();display=new DisplayDefinition(719,575,0,719,0,575);}

    void segment(int type,int pageId,byte[] data,int off,int len){
        if(data==null||off<0||len<0||off>data.length-len){malformedSegments++;return;}
        try{
            switch(type){
                case 0x14: if(pageId==compositionPageId)display=parseDisplay(data,off,len);break;
                case 0x10:
                    if(pageId==compositionPageId){PageComposition next=parsePage(data,off,len);PageComposition prior=page;
                        if(next.state!=PAGE_STATE_NORMAL){page=next;regions.clear();cluts.clear();objects.clear();}
                        else if(prior==null||prior.version!=next.version)page=next;
                    }break;
                case 0x11:
                    if(pageId==compositionPageId&&page!=null){RegionComposition next=parseRegion(data,off,len);if(page.state==PAGE_STATE_NORMAL){RegionComposition prior=regions.get(Integer.valueOf(next.id));if(prior!=null)next.mergeFrom(prior);}regions.put(Integer.valueOf(next.id),next);}break;
                case 0x12:
                    if(pageId==compositionPageId){ClutDefinition c=parseClut(data,off,len);cluts.put(Integer.valueOf(c.id),c);}else if(pageId==ancillaryPageId){ClutDefinition c=parseClut(data,off,len);ancillaryCluts.put(Integer.valueOf(c.id),c);}break;
                case 0x13:
                    if(pageId==compositionPageId){ObjectData o=parseObject(data,off,len);objects.put(Integer.valueOf(o.id),o);}else if(pageId==ancillaryPageId){ObjectData o=parseObject(data,off,len);ancillaryObjects.put(Integer.valueOf(o.id),o);}break;
                default: break;
            }
        }catch(RuntimeException e){malformedSegments++;}
    }

    Frame render(){
        int cw=Math.min(SubtitleLimits.MAX_BITMAP_WIDTH,Math.max(1,display.width+1));
        int ch=Math.min(SubtitleLimits.MAX_BITMAP_HEIGHT,Math.max(1,display.height+1));
        if((long)cw*ch>SubtitleLimits.MAX_BITMAP_PIXELS||page==null)return new Frame(new ArrayList<BitmapRect>(),page==null?0:page.timeout,cw,ch);
        List<BitmapRect> out=new ArrayList<BitmapRect>();
        for(Map.Entry<Integer,PageRegion> entry:page.pageRegions.entrySet()){
            RegionComposition region=regions.get(entry.getKey());if(region==null)continue;
            PageRegion pr=entry.getValue();int x=pr.x+display.xMin,y=pr.y+display.yMin;
            int w=Math.min(region.width,Math.min(cw-x,display.xMax-x+1));int h=Math.min(region.height,Math.min(ch-y,display.yMax-y+1));
            if(x<0||y<0||w<=0||h<=0||w>SubtitleLimits.MAX_BITMAP_WIDTH||h>SubtitleLimits.MAX_BITMAP_HEIGHT||(long)w*h*4>SubtitleLimits.MAX_CUE_BYTES)continue;
            byte[] rgba=new byte[w*h*4];ClutDefinition clut=cluts.get(Integer.valueOf(region.clutId));if(clut==null)clut=ancillaryCluts.get(Integer.valueOf(region.clutId));if(clut==null)clut=ClutDefinition.DEFAULT;
            if(region.fill){int code=region.depth==3?region.pixel8:region.depth==2?region.pixel4:region.pixel2;fill(rgba,w,h,color(clut,region.depth,code));}
            for(Map.Entry<Integer,RegionObject> oe:region.objects.entrySet()){
                RegionObject ro=oe.getValue();ObjectData object=objects.get(oe.getKey());if(object==null)object=ancillaryObjects.get(oe.getKey());if(object==null)continue;
                paintField(object.top,rgba,w,h,region.depth,ro.x,ro.y,clut,object.nonModifying);
                paintField(object.bottom,rgba,w,h,region.depth,ro.x,ro.y+1,clut,object.nonModifying);
            }
            out.add(new BitmapRect(cw,ch,x,y,w,h,rgba));
        }
        renderedFrames++;return new Frame(out,page.timeout,cw,ch);
    }

    private static DisplayDefinition parseDisplay(byte[] b,int off,int len){BitReader r=new BitReader(b,off,len);r.skip(4);boolean window=r.bit();r.skip(3);int width=r.bits(16),height=r.bits(16);int x0=0,x1=width,y0=0,y1=height;if(window){x0=r.bits(16);x1=r.bits(16);y0=r.bits(16);y1=r.bits(16);}return new DisplayDefinition(width,height,x0,x1,y0,y1);}
    private static PageComposition parsePage(byte[] b,int off,int len){BitReader r=new BitReader(b,off,len);int timeout=r.bits(8),version=r.bits(4),state=r.bits(2);r.skip(2);Map<Integer,PageRegion> ps=new LinkedHashMap<Integer,PageRegion>();while(r.bitsLeft()>=48){int id=r.bits(8);r.skip(8);ps.put(Integer.valueOf(id),new PageRegion(r.bits(16),r.bits(16)));}return new PageComposition(timeout,version,state,ps);}
    private static RegionComposition parseRegion(byte[] b,int off,int len){BitReader r=new BitReader(b,off,len);int id=r.bits(8);r.skip(4);boolean fill=r.bit();r.skip(3);int width=r.bits(16),height=r.bits(16);int compatibility=r.bits(3),depth=r.bits(3);r.skip(2);int clut=r.bits(8),p8=r.bits(8),p4=r.bits(4),p2=r.bits(2);r.skip(2);Map<Integer,RegionObject> os=new LinkedHashMap<Integer,RegionObject>();while(r.bitsLeft()>=48){int oid=r.bits(16),type=r.bits(2),provider=r.bits(2),x=r.bits(12);r.skip(4);int y=r.bits(12),fg=0,bg=0;if((type==1||type==2)&&r.bitsLeft()>=16){fg=r.bits(8);bg=r.bits(8);}os.put(Integer.valueOf(oid),new RegionObject(type,provider,x,y,fg,bg));}return new RegionComposition(id,fill,width,height,compatibility,depth,clut,p8,p4,p2,os);}
    private static ClutDefinition parseClut(byte[] b,int off,int len){BitReader r=new BitReader(b,off,len);int id=r.bits(8);r.skip(8);int[] c2=default2(),c4=default4(),c8=default8();while(r.bitsLeft()>=16){int eid=r.bits(8),flags=r.bits(8);int[] target=(flags&0x80)!=0?c2:(flags&0x40)!=0?c4:c8;int y,cr,cb,t;if((flags&1)!=0){if(r.bitsLeft()<32)break;y=r.bits(8);cr=r.bits(8);cb=r.bits(8);t=r.bits(8);}else{if(r.bitsLeft()<16)break;y=r.bits(6)<<2;cr=r.bits(4)<<4;cb=r.bits(4)<<4;t=r.bits(2)<<6;}if(y==0){cr=0;cb=0;t=255;}int a=255-(t&255),rr=clamp((int)(y+1.40200*(cr-128))),g=clamp((int)(y-0.34414*(cb-128)-0.71414*(cr-128))),bb=clamp((int)(y+1.77200*(cb-128)));if(eid<target.length)target[eid]=argb(a,rr,g,bb);}return new ClutDefinition(id,c2,c4,c8);}
    private static ObjectData parseObject(byte[] b,int off,int len){BitReader r=new BitReader(b,off,len);int id=r.bits(16);r.skip(4);int coding=r.bits(2);boolean non=r.bit();r.skip(1);byte[] top=new byte[0],bottom=new byte[0];if(coding==1){int n=r.bits(8);r.skip(Math.min(r.bitsLeft(),n*16));}else if(coding==0){int tl=r.bits(16),bl=r.bits(16);if(tl<0||bl<0||tl>r.bytesLeft()||tl+bl>r.bytesLeft())throw new IllegalArgumentException("bad object field length");top=r.bytes(tl);bottom=bl>0?r.bytes(bl):top;}return new ObjectData(id,non,top,bottom);}

    private static void fill(byte[] rgba,int w,int h,int argb){for(int y=0;y<h;y++)paintRun(rgba,w,h,0,y,w,argb,false);}
    private static int color(ClutDefinition c,int depth,int code){int[] a=depth==3?c.c8:depth==2?c.c4:c.c2;return a[Math.max(0,Math.min(a.length-1,code))];}
    private static void paintField(byte[] data,byte[] rgba,int w,int h,int depth,int startX,int startY,ClutDefinition clut,boolean nonModifying){if(data==null||data.length==0)return;BitReader r=new BitReader(data,0,data.length);int x=startX,y=startY;byte[] m24=null,m28=null,m48=null;while(r.bitsLeft()>=8){int type=r.bits(8);try{switch(type){case 0x10:{byte[] map=depth==3?(m28==null?DEFAULT_MAP_2_TO_8:m28):depth==2?(m24==null?DEFAULT_MAP_2_TO_4:m24):null;x=paint2(r,rgba,w,h,x,y,clut,depth,map,nonModifying);r.align();break;}case 0x11:{byte[] map=depth==3?(m48==null?DEFAULT_MAP_4_TO_8:m48):null;x=paint4(r,rgba,w,h,x,y,clut,depth,map,nonModifying);r.align();break;}case 0x12:x=paint8(r,rgba,w,h,x,y,clut,nonModifying);break;case 0x20:m24=table(r,4,4);break;case 0x21:m28=table(r,4,8);break;case 0x22:m48=table(r,16,8);break;case 0xf0:x=startX;y+=2;break;default:break;}}catch(IllegalArgumentException ex){break;}}}
    private static int paint2(BitReader r,byte[] rgba,int w,int h,int x,int y,ClutDefinition c,int depth,byte[] map,boolean non){boolean end=false;while(!end&&r.bitsLeft()>=2){int run=0,idx=0,peek=r.bits(2);if(peek!=0){run=1;idx=peek;}else if(r.bit()){run=3+r.bits(3);idx=r.bits(2);}else if(r.bit()){run=1;}else{switch(r.bits(2)){case 0:end=true;break;case 1:run=2;break;case 2:run=12+r.bits(4);idx=r.bits(2);break;case 3:run=29+r.bits(8);idx=r.bits(2);break;}}if(run>0){int mapped=map==null?idx:(map[idx]&255);paintRun(rgba,w,h,x,y,run,color(c,depth,mapped),non&&mapped==1);x+=run;}}return x;}
    private static int paint4(BitReader r,byte[] rgba,int w,int h,int x,int y,ClutDefinition c,int depth,byte[] map,boolean non){boolean end=false;while(!end&&r.bitsLeft()>=4){int run=0,idx=0,peek=r.bits(4);if(peek!=0){run=1;idx=peek;}else if(!r.bit()){peek=r.bits(3);if(peek!=0)run=2+peek;else end=true;}else if(!r.bit()){run=4+r.bits(2);idx=r.bits(4);}else{switch(r.bits(2)){case 0:run=1;break;case 1:run=2;break;case 2:run=9+r.bits(4);idx=r.bits(4);break;case 3:run=25+r.bits(8);idx=r.bits(4);break;}}if(run>0){int mapped=map==null?idx:(map[idx]&255);paintRun(rgba,w,h,x,y,run,color(c,depth,mapped),non&&mapped==1);x+=run;}}return x;}
    private static int paint8(BitReader r,byte[] rgba,int w,int h,int x,int y,ClutDefinition c,boolean non){boolean end=false;while(!end&&r.bitsLeft()>=8){int run=0,idx=0,peek=r.bits(8);if(peek!=0){run=1;idx=peek;}else if(!r.bit()){run=r.bits(7);if(run==0)end=true;}else{run=r.bits(7);if(run>0)idx=r.bits(8);}if(run>0){paintRun(rgba,w,h,x,y,run,color(c,3,idx),non&&idx==1);x+=run;}}return x;}
    private static byte[] table(BitReader r,int count,int bits){byte[] out=new byte[count];for(int i=0;i<count;i++)out[i]=(byte)r.bits(bits);return out;}
    private static void paintRun(byte[] rgba,int w,int h,int x,int y,int run,int argb,boolean skip){if(skip||y<0||y>=h||run<=0)return;int from=Math.max(0,x),to=Math.min(w,x+run);if(to<=from)return;byte a=(byte)(argb>>>24),rr=(byte)(argb>>>16),g=(byte)(argb>>>8),b=(byte)argb;for(int xx=from;xx<to;xx++){int p=(y*w+xx)*4;rgba[p]=rr;rgba[p+1]=g;rgba[p+2]=b;rgba[p+3]=a;}}
    private static int[] default2(){return new int[]{0x00000000,0xffffffff,0xff000000,0xff7f7f7f};}
    private static int[] default4(){int[] e=new int[16];for(int i=1;i<16;i++)e[i]=argb(255,(i&1)!=0?(i<8?255:127):0,(i&2)!=0?(i<8?255:127):0,(i&4)!=0?(i<8?255:127):0);return e;}
    private static int[] default8(){int[] e=new int[256];e[0]=0;for(int i=1;i<256;i++){if(i<8)e[i]=argb(0x3f,(i&1)!=0?255:0,(i&2)!=0?255:0,(i&4)!=0?255:0);else{int r,g,b,a;switch(i&0x88){case 0:a=255;r=((i&1)!=0?0x55:0)+((i&0x10)!=0?0xaa:0);g=((i&2)!=0?0x55:0)+((i&0x20)!=0?0xaa:0);b=((i&4)!=0?0x55:0)+((i&0x40)!=0?0xaa:0);break;case 0x08:a=127;r=((i&1)!=0?0x55:0)+((i&0x10)!=0?0xaa:0);g=((i&2)!=0?0x55:0)+((i&0x20)!=0?0xaa:0);b=((i&4)!=0?0x55:0)+((i&0x40)!=0?0xaa:0);break;case 0x80:a=255;r=127+((i&1)!=0?0x2b:0)+((i&0x10)!=0?0x55:0);g=127+((i&2)!=0?0x2b:0)+((i&0x20)!=0?0x55:0);b=127+((i&4)!=0?0x2b:0)+((i&0x40)!=0?0x55:0);break;default:a=255;r=((i&1)!=0?0x2b:0)+((i&0x10)!=0?0x55:0);g=((i&2)!=0?0x2b:0)+((i&0x20)!=0?0x55:0);b=((i&4)!=0?0x2b:0)+((i&0x40)!=0?0x55:0);}e[i]=argb(a,r,g,b);}}return e;}
    private static int argb(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}private static int clamp(int v){return Math.max(0,Math.min(255,v));}

    private static final class DisplayDefinition {final int width,height,xMin,xMax,yMin,yMax;DisplayDefinition(int w,int h,int x0,int x1,int y0,int y1){width=w;height=h;xMin=x0;xMax=x1;yMin=y0;yMax=y1;}}
    private static final class PageRegion {final int x,y;PageRegion(int x,int y){this.x=x;this.y=y;}}
    private static final class PageComposition {final int timeout,version,state;final Map<Integer,PageRegion> pageRegions;PageComposition(int t,int v,int s,Map<Integer,PageRegion> r){timeout=t;version=v;state=s;pageRegions=r;}}
    private static final class RegionObject {final int type,provider,x,y,fg,bg;RegionObject(int t,int p,int x,int y,int fg,int bg){type=t;provider=p;this.x=x;this.y=y;this.fg=fg;this.bg=bg;}}
    private static final class RegionComposition {final int id,width,height,compatibility,depth,clutId,pixel8,pixel4,pixel2;final boolean fill;final Map<Integer,RegionObject> objects;RegionComposition(int id,boolean f,int w,int h,int c,int d,int clut,int p8,int p4,int p2,Map<Integer,RegionObject> o){this.id=id;fill=f;width=w;height=h;compatibility=c;depth=d;clutId=clut;pixel8=p8;pixel4=p4;pixel2=p2;objects=o;}void mergeFrom(RegionComposition old){for(Map.Entry<Integer,RegionObject> e:old.objects.entrySet())if(!objects.containsKey(e.getKey()))objects.put(e.getKey(),e.getValue());}}
    private static final class ClutDefinition {static final ClutDefinition DEFAULT=new ClutDefinition(0,default2(),default4(),default8());final int id;final int[] c2,c4,c8;ClutDefinition(int id,int[] a,int[] b,int[] c){this.id=id;c2=a;c4=b;c8=c;}}
    private static final class ObjectData {final int id;final boolean nonModifying;final byte[] top,bottom;ObjectData(int id,boolean n,byte[] t,byte[] b){this.id=id;nonModifying=n;top=t;bottom=b;}}
    private static final class BitReader {final byte[] b;final int endBit;int bit;BitReader(byte[] b,int off,int len){this.b=b;bit=off*8;endBit=(off+len)*8;}int bitsLeft(){return endBit-bit;}int bytesLeft(){return bitsLeft()/8;}boolean bit(){return bits(1)!=0;}int bits(int n){if(n<0||n>31||bitsLeft()<n)throw new IllegalArgumentException("truncated DVB segment");int v=0;for(int i=0;i<n;i++){v=(v<<1)|((b[bit>>3]>>(7-(bit&7)))&1);bit++;}return v;}void skip(int n){if(n<0||bitsLeft()<n)throw new IllegalArgumentException("truncated DVB segment");bit+=n;}void align(){bit=(bit+7)&~7;if(bit>endBit)bit=endBit;}byte[] bytes(int n){align();if(n<0||bitsLeft()<n*8)throw new IllegalArgumentException("truncated DVB bytes");int p=bit>>3;bit+=n*8;return Arrays.copyOfRange(b,p,p+n);}}
}
