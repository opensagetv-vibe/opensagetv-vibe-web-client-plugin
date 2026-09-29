package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/** HTTP control/long-poll bridge for the browser Vibe MiniClient. */
public final class MiniClientServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException { handle(req,resp,false); }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException { handle(req,resp,true); }

    private void handle(HttpServletRequest req,HttpServletResponse resp,boolean post)throws IOException{
        req.setCharacterEncoding("UTF-8");
        String action=param(req,"action","status");
        try{
            if (!post && !("status".equals(action) || "events".equals(action) || "stream".equals(action) || "streamSettings".equals(action))) {
                resp.setHeader("Allow", "POST"); HttpUtil.jsonError(resp,405,"Use POST for MiniClient controls"); return;
            }
            if("start".equals(action)){
                String server=param(req,"server","");
                if(server.length()==0){ try{server=SageApiBridge.create().getServerAddress();}catch(Exception ignored){} }
                MiniClientSession s=MiniClientSessionManager.start(server,param(req,"clientId",""),
                        HttpUtil.optionalInt(req.getParameter("width"),1280,"width"),
                        HttpUtil.optionalInt(req.getParameter("height"),720,"height"), StreamOptions.fromRequest(req::getParameter), booleanParam(req,"unifiedGraphics",false));
                json(resp,s.json()); return;
            }
            String id=req.getParameter("session");
            MiniClientSession s=MiniClientSessionManager.get(id);
            if(s==null){HttpUtil.jsonError(resp,404,"Unknown MiniClient session");return;}
            if("stream".equals(action)){
                resp.setCharacterEncoding("UTF-8");resp.setContentType("application/x-ndjson");
                resp.setHeader("Cache-Control","no-store, no-cache, must-revalidate");
                resp.setHeader("X-Accel-Buffering","no");
                java.io.PrintWriter out=resp.getWriter();
                try{
                    while(s.isAlive()||s.hasEvents()){
                        List<String> items=s.pollEvents(500,15000L);
                        if(items.isEmpty()){out.write("{\"type\":\"keepalive\"}\n");}
                        else for(String item:items){out.write(item);out.write('\n');}
                        out.flush();
                        if(out.checkError())break;
                    }
                }catch(InterruptedException e){Thread.currentThread().interrupt();}
                catch(Exception ignored){}
                return;
            }
            if("events".equals(action)){
                int max=Math.max(1,Math.min(500,HttpUtil.optionalInt(req.getParameter("max"),100,"max")));
                long wait=Math.max(0,Math.min(30000,HttpUtil.optionalInt(req.getParameter("wait"),25000,"wait")));
                List<String> items;
                try{items=s.pollEvents(max,wait);}catch(InterruptedException e){Thread.currentThread().interrupt();items=java.util.Collections.emptyList();}
                StringBuilder b=new StringBuilder("[");
                for(int i=0;i<items.size();i++){if(i>0)b.append(',');b.append(items.get(i));}
                b.append(']');json(resp,b.toString());return;
            }
            if("streamSettings".equals(action)) {
                if(post) {
                    StreamOptions options=StreamOptions.fromRequest(req::getParameter);
                    boolean ok=s.media().applySettings(options,"true".equals(req.getParameter("applyNow")),param(req,"expectedHlsSession",""),longParam(req,"timeMs",-1));
                    if(!ok){json(resp,"{\"ok\":false,\"stale\":true}");return;}
                }
                json(resp,s.media().streamingJson());return;
            }
            if("fallback".equals(action)) {
                boolean ok=s.media().fallback(param(req,"expectedHlsSession",""),longParam(req,"timeMs",-1));
                json(resp,"{\"ok\":"+ok+",\"stale\":"+(!ok)+"}");return;
            }
            if("status".equals(action)){json(resp,s.json());return;}
            if("stop".equals(action)){MiniClientSessionManager.stop(id);json(resp,"{\"ok\":true}");return;}
            if("command".equals(action)){s.sendSageCommand(HttpUtil.requiredInt(req.getParameter("command"),"command"));json(resp,"{\"ok\":true}");return;}
            if("key".equals(action)){s.sendKey(HttpUtil.optionalInt(req.getParameter("code"),0,"code"),HttpUtil.optionalInt(req.getParameter("modifiers"),0,"modifiers"),HttpUtil.optionalInt(req.getParameter("char"),0,"char"));json(resp,"{\"ok\":true}");return;}
            if("mouse".equals(action)){s.sendMouse(HttpUtil.requiredInt(req.getParameter("type"),"type"),HttpUtil.optionalInt(req.getParameter("x"),0,"x"),HttpUtil.optionalInt(req.getParameter("y"),0,"y"),HttpUtil.optionalInt(req.getParameter("button"),0,"button"),HttpUtil.optionalInt(req.getParameter("modifiers"),0,"modifiers"),HttpUtil.optionalInt(req.getParameter("clicks"),1,"clicks"));json(resp,"{\"ok\":true}");return;}
            if("resize".equals(action)){s.sendResize(HttpUtil.requiredInt(req.getParameter("width"),"width"),HttpUtil.requiredInt(req.getParameter("height"),"height"));json(resp,"{\"ok\":true}");return;}
            if("repaint".equals(action)){s.sendRepaint(HttpUtil.optionalInt(req.getParameter("x"),0,"x"),HttpUtil.optionalInt(req.getParameter("y"),0,"y"),HttpUtil.optionalInt(req.getParameter("width"),s.width(),"width"),HttpUtil.optionalInt(req.getParameter("height"),s.height(),"height"));json(resp,"{\"ok\":true}");return;}
            if("recover".equals(action) || "seek".equals(action)){
                long time = longParam(req,"timeMs",-1L);
                if ("seek".equals(action) && time < 0L) throw new IllegalArgumentException("timeMs is required");
                String expected = param(req,"expectedHlsSession","");
                boolean applied = "recover".equals(action) ? s.recoverMedia(expected,time) : s.seekMedia(expected,time);
                json(resp,"{\"ok\":"+applied+",\"stale\":"+(!applied)+"}"); return;
            }
            if("click".equals(action)){
                s.sendMouseClick(HttpUtil.requiredInt(req.getParameter("x"),"x"),HttpUtil.requiredInt(req.getParameter("y"),"y"),
                    HttpUtil.optionalInt(req.getParameter("button"),1,"button"),!"false".equals(req.getParameter("includeClick")));
                json(resp,"{\"ok\":true}");return;
            }
            if("text".equals(action)){s.sendText(req.getParameter("text"));json(resp,"{\"ok\":true}");return;}
            if("mediaUpdate".equals(action)){s.sendMediaPlayerUpdate();json(resp,"{\"ok\":true}");return;}
            if("state".equals(action)){
                long time=0; try{time=Long.parseLong(param(req,"timeMs","0"));}catch(Exception ignored){}
                float volume=1f; try{volume=Float.parseFloat(param(req,"volume","1"));}catch(Exception ignored){}
                s.reportBrowserState(param(req,"expectedHlsSession",""),time,HttpUtil.optionalInt(req.getParameter("videoWidth"),0,"videoWidth"),HttpUtil.optionalInt(req.getParameter("videoHeight"),0,"videoHeight"),volume,"true".equalsIgnoreCase(req.getParameter("paused")),"true".equalsIgnoreCase(req.getParameter("ended")));
                json(resp,"{\"ok\":true}");return;
            }
            HttpUtil.jsonError(resp,400,"Unknown MiniClient action: "+action);
        }catch(IllegalArgumentException e){HttpUtil.jsonError(resp,400,e.getMessage());}
        catch(Exception e){HttpUtil.jsonError(resp,500,e.getMessage()==null?e.toString():e.getMessage());}
    }
    @Override public void destroy(){MiniClientSessionManager.stopAll();HlsSessionManager.stopAll();super.destroy();}

    private static boolean booleanParam(HttpServletRequest req,String name,boolean def) {
        String value=req.getParameter(name);if(value==null)return def;
        if("true".equalsIgnoreCase(value)||"1".equals(value))return true;
        if("false".equalsIgnoreCase(value)||"0".equals(value))return false;
        throw new IllegalArgumentException("Invalid "+name);
    }

    private static long longParam(HttpServletRequest req,String name,long def) {
        String value=req.getParameter(name); if(value==null) return def;
        try { return Long.parseLong(value); } catch(NumberFormatException e) { throw new IllegalArgumentException("Invalid " + name); }
    }
    private static String param(HttpServletRequest req,String name,String def){String v=req.getParameter(name);return v==null?def:v.trim();}
    private static void json(HttpServletResponse resp,String body)throws IOException{resp.setCharacterEncoding("UTF-8");resp.setContentType("application/json");resp.setHeader("Cache-Control","no-store");resp.getWriter().write(body);}
}
