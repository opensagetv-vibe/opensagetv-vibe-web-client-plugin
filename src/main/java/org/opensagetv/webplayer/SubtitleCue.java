package org.opensagetv.webplayer;

import java.util.Base64;

/** Versioned transport-neutral cue. Bitmap payloads are straight-alpha RGBA or encoded PNG as identified by format. */
final class SubtitleCue {
    static final int CONTRACT_VERSION=1;
    enum Kind { TEXT, BITMAP }
    enum Owner { LOCAL_BROADCAST, LOCAL_DVB, LOCAL_FILE, DVD_SPU, LEGACY_STV }
    final PlaybackSessionContext.Token token;
    final Kind kind; final Owner owner; final long ptsMs,durationMs; final boolean clear,end;
    final int canvasWidth,canvasHeight,x,y,width,height,linePercent,positionPercent; final String text,format,align; final byte[] payload;

    private SubtitleCue(PlaybackSessionContext.Token token,Kind kind,Owner owner,long ptsMs,long durationMs,boolean clear,boolean end,
            int canvasWidth,int canvasHeight,int x,int y,int width,int height,String text,String format,byte[] payload,int linePercent,int positionPercent,String align){
        if(token==null)throw new IllegalArgumentException("cue token required");
        if(ptsMs<0||durationMs<0)throw new IllegalArgumentException("negative cue time");
        if(kind==Kind.TEXT){
            String t=text==null?"":text;if(t.length()>SubtitleLimits.MAX_TEXT_CHARS)throw new IllegalArgumentException("subtitle text limit exceeded");
            this.text=t;this.payload=new byte[0];this.format="text";
        }else{
            if(canvasWidth<1||canvasHeight<1||canvasWidth>SubtitleLimits.MAX_BITMAP_WIDTH||canvasHeight>SubtitleLimits.MAX_BITMAP_HEIGHT||(long)canvasWidth*canvasHeight>SubtitleLimits.MAX_BITMAP_PIXELS)throw new IllegalArgumentException("subtitle canvas limit exceeded");
            if(x<0||y<0||width<0||height<0||x+width>canvasWidth||y+height>canvasHeight)throw new IllegalArgumentException("subtitle rectangle out of bounds");
            byte[] p=payload==null?new byte[0]:payload;if(p.length>SubtitleLimits.MAX_CUE_BYTES)throw new IllegalArgumentException("subtitle cue payload limit exceeded");
            this.payload=p.clone();this.text="";this.format=format==null?"rgba-straight":format;
        }
        this.token=token;this.kind=kind;this.owner=owner;this.ptsMs=ptsMs;this.durationMs=durationMs;this.clear=clear;this.end=end;
        this.canvasWidth=canvasWidth;this.canvasHeight=canvasHeight;this.x=x;this.y=y;this.width=width;this.height=height;this.linePercent=linePercent;this.positionPercent=positionPercent;this.align=align==null?"center":align;
    }
    static SubtitleCue text(PlaybackSessionContext.Token t,Owner o,long pts,long dur,String text,boolean clear,boolean end){return textPositioned(t,o,pts,dur,text,clear,end,-1,50,"center");}
    static SubtitleCue textPositioned(PlaybackSessionContext.Token t,Owner o,long pts,long dur,String text,boolean clear,boolean end,int line,int position,String align){return new SubtitleCue(t,Kind.TEXT,o,pts,dur,clear,end,0,0,0,0,0,0,text,"text",null,line,position,align);}
    static SubtitleCue bitmap(PlaybackSessionContext.Token t,Owner o,long pts,long dur,int cw,int ch,int x,int y,int w,int h,String format,byte[] payload,boolean clear,boolean end){return new SubtitleCue(t,Kind.BITMAP,o,pts,dur,clear,end,cw,ch,x,y,w,h,"",format,payload,-1,50,"center");}
    int bytes(){return kind==Kind.TEXT?text.length()*2:payload.length;}
    String json(){StringBuilder b=new StringBuilder("{\"v\":1,\"session\":").append(token.json()).append(",\"kind\":\"").append(kind.name().toLowerCase()).append("\",\"owner\":\"").append(owner.name().toLowerCase()).append("\",\"ptsMs\":").append(ptsMs).append(",\"durationMs\":").append(durationMs).append(",\"clear\":").append(clear).append(",\"end\":").append(end);
        if(kind==Kind.TEXT)b.append(",\"text\":\"").append(HttpUtil.json(text)).append('"').append(",\"linePercent\":").append(linePercent).append(",\"positionPercent\":").append(positionPercent).append(",\"align\":\"").append(HttpUtil.json(align)).append('"');
        else b.append(",\"canvas\":{").append("\"width\":").append(canvasWidth).append(",\"height\":").append(canvasHeight).append("},\"rect\":[").append(x).append(',').append(y).append(',').append(width).append(',').append(height).append("],\"format\":\"").append(HttpUtil.json(format)).append("\",\"data\":\"").append(Base64.getEncoder().encodeToString(payload)).append('"');
        return b.append('}').toString();}
}
