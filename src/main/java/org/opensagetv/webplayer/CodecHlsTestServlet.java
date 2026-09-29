package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Fixed-input control API for the isolated native-HLS codec test page. */
public class CodecHlsTestServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        write(resp,CodecHlsTestManager.status().json());
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        String action=req.getParameter("action");
        if(action==null||action.length()==0)action="generate";
        if(!"generate".equals(action)){HttpUtil.jsonError(resp,400,"Unsupported codec-test action");return;}
        boolean force="1".equals(req.getParameter("force"))||"true".equalsIgnoreCase(req.getParameter("force"));
        try{write(resp,CodecHlsTestManager.generate(force).json());}
        catch(IOException e){HttpUtil.jsonError(resp,502,e.getMessage());}
    }
    private static void write(HttpServletResponse resp,String json)throws IOException{
        resp.setCharacterEncoding("UTF-8");resp.setContentType("application/json");
        resp.setHeader("Cache-Control","no-store, no-cache, must-revalidate, max-age=0");resp.getWriter().write(json);
    }
}
