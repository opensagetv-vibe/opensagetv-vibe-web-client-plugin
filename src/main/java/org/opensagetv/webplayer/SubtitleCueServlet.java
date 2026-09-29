package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/** Versioned P02 cue-channel endpoint. No decoder is enabled merely by exposing this contract. */
public final class SubtitleCueServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        HlsSessionManager.Session s=HlsSessionManager.get(req.getParameter("session"));
        if(s==null){HttpUtil.jsonError(resp,404,"Unknown streaming session");return;}
        try{
            long time=ContinuousStreamServlet.parseOffset(req.getParameter("timeMs"));if(time>7L*86400000L)throw new IllegalArgumentException("Invalid subtitle time");
            int max=HttpUtil.optionalInt(req.getParameter("max"),128,"max");if(max<1||max>512)throw new IllegalArgumentException("max must be 1..512");
            boolean dvdScope="dvd".equalsIgnoreCase(req.getParameter("scope"));
            boolean fileScope="file".equalsIgnoreCase(req.getParameter("scope"));
            PlaybackSessionContext.Token token; SubtitleCueQueue queue;
            if(dvdScope){if(s.dvdSource==null){HttpUtil.jsonError(resp,409,"This stream has no DVD SPU context");return;}token=s.dvdSource.spu().token();queue=s.dvdSource.spu().queue();}
            else {if(s.playbackContext==null||s.playbackToken==null){HttpUtil.jsonError(resp,409,"This stream has no subtitle cue context");return;}token=s.playbackToken;queue=fileScope?s.playbackContext.fileCues():s.playbackContext.cues();}
            List<SubtitleCue> cues=queue.drainDue(token,time,max);
            StringBuilder b=new StringBuilder("{\"contractVersion\":1,\"session\":").append(token.json()).append(",\"cues\":[");
            for(int i=0;i<cues.size();i++){if(i>0)b.append(',');b.append(cues.get(i).json());}b.append("],\"scope\":\"").append(dvdScope?"dvd":fileScope?"file":"broadcast").append("\",\"queue\":").append(queue.json(time))
                    .append(",\"teletext\":").append(s.teletext==null?"null":s.teletext.stateJson())
                    .append(",\"dvbBitmap\":").append(s.dvb==null?"null":s.dvb.stateJson())
                    .append(",\"ordinary\":").append(s.ordinarySubtitles==null?"null":s.ordinarySubtitles.stateJson())
                    .append(",\"dvdSpu\":").append(s.dvdSource==null?"null":s.dvdSource.spu().json()).append('}');
            resp.setCharacterEncoding("UTF-8");resp.setContentType("application/json");resp.setHeader("Cache-Control","no-store");resp.getWriter().write(b.toString());
        }catch(IllegalArgumentException e){HttpUtil.jsonError(resp,400,e.getMessage());}
    }
}
