package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** P14 secret-safe report export and opt-in server-destination workflow. */
public final class DiagnosticSupportServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        String action=param(req,"action","status");
        if(!"status".equals(action)){resp.setHeader("Allow","GET, POST");HttpUtil.jsonError(resp,405,"Use POST for diagnostic mutations");return;}
        json(resp,DiagnosticSupport.statusJson());
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        req.setCharacterEncoding("UTF-8");String action=param(req,"action","");
        if("cancel".equals(action)){String id=param(req,"operationId","");json(resp,"{\"cancelled\":"+DiagnosticSupport.cancelOperation(id)+"}");return;}
        if("testDestination".equals(action)){if(!confirmed(req)){HttpUtil.jsonError(resp,400,"confirm=true is required for the bounded server write test");return;}String id=param(req,"operationId","");AtomicBoolean cancel=DiagnosticSupport.beginOperation(id);try{json(resp,DiagnosticSupport.testDestination(cancel).json());}finally{DiagnosticSupport.endOperation(id,cancel);}return;}
        if("retrySpool".equals(action)){if(!confirmed(req)){HttpUtil.jsonError(resp,400,"confirm=true is required to retry server diagnostic writes");return;}String id=param(req,"operationId","");AtomicBoolean cancel=DiagnosticSupport.beginOperation(id);try{json(resp,DiagnosticSupport.retrySpool(cancel).json());}finally{DiagnosticSupport.endOperation(id,cancel);}return;}
        if(!"report".equals(action)){HttpUtil.jsonError(resp,400,"Unknown support action");return;}
        String browser,test;
        try{browser=bounded(req.getParameter("browserJson"));test=bounded(req.getParameter("videoTestJson"));}
        catch(IllegalArgumentException e){HttpUtil.jsonError(resp,413,e.getMessage());return;}
        String mini="null",hls="null";MiniClientSession ms=MiniClientSessionManager.get(param(req,"session",""));if(ms!=null)mini=ms.json();HlsSessionManager.Session hs=HlsSessionManager.get(param(req,"hlsSession",""));if(hs!=null)hls=HlsSessionManager.json(hs);
        byte[] zip=DiagnosticSupport.zip(browser,mini,hls,DiagnosticSupport.transcoderJson(),test);
        DiagnosticSupport.SaveResult save="true".equalsIgnoreCase(req.getParameter("saveToServer"))?DiagnosticSupport.save(zip):new DiagnosticSupport.SaveResult("not-requested","Browser download only","");
        resp.setStatus(200);resp.setContentType("application/zip");resp.setHeader("Cache-Control","no-store");resp.setHeader("Content-Disposition","attachment; filename=\"SageTV-WebPlayer-diagnostics.zip\"");resp.setHeader("X-SageTV-Report-Save",save.state);resp.setHeader("X-SageTV-Report-Detail",safeHeader(save.detail));resp.setContentLength(zip.length);resp.getOutputStream().write(zip);
    }
    private static String bounded(String s){if(s==null||s.trim().isEmpty())return "null";if(s.length()>DiagnosticSupport.MAX_BROWSER_JSON)throw new IllegalArgumentException("Diagnostic browser snapshot exceeds bounded size");return s;}
    private static boolean confirmed(HttpServletRequest req){return "true".equalsIgnoreCase(req.getParameter("confirm"));}
    private static String param(HttpServletRequest req,String k,String d){String v=req.getParameter(k);return v==null?d:v.trim();}
    private static String safeHeader(String v){if(v==null)return "";String clean=v.replace('\r',' ').replace('\n',' ');return clean.substring(0,Math.min(240,clean.length()));}
    private static void json(HttpServletResponse resp,String body)throws IOException{resp.setCharacterEncoding("UTF-8");resp.setContentType("application/json");resp.setHeader("Cache-Control","no-store");resp.getWriter().write(body);}
}
