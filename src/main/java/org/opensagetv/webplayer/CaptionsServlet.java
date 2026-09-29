package org.opensagetv.webplayer;
import javax.servlet.http.*;
import java.io.*;

/** Timed A/53 packets only; this endpoint never accepts an arbitrary recording path or URL. */
public final class CaptionsServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        HlsSessionManager.Session s=HlsSessionManager.get(req.getParameter("session"));
        if(s==null){HttpUtil.jsonError(resp,404,"Unknown streaming session");return;}
        if(s.options==null||!s.options.captionsEnabled()){HttpUtil.jsonError(resp,409,"Local broadcast captions are off for this stream");return;}
        try {
            long cursor=ContinuousStreamServlet.parseOffset(req.getParameter("cursor"));
            long until=ContinuousStreamServlet.parseOffset(req.getParameter("untilMs"));
            if(until>7L*86400000L)throw new IllegalArgumentException("Invalid caption time");
            resp.setCharacterEncoding("UTF-8");resp.setContentType("application/json");resp.setHeader("Cache-Control","no-store");
            resp.getWriter().write(A53CaptionTap.read(new File(s.directory,"captions.bin"),cursor,until));
        }catch(IllegalArgumentException e){HttpUtil.jsonError(resp,400,e.getMessage());}
    }
}
