package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Read-only disc/source/title diagnostic endpoint. No arbitrary path parameter is accepted. */
public final class DiscInfoServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException{
        response.setCharacterEncoding("UTF-8");response.setContentType("application/json");response.setHeader("Cache-Control","no-store");
        try{
            int id=HttpUtil.requiredInt(request.getParameter("id"),"id");SageApiBridge sage=SageApiBridge.create();Object mf=sage.getMediaFile(id);if(mf==null){HttpUtil.jsonError(response,404,"SageTV MediaFile not found: "+id);return;}
            DiscInspection d=DiscInspection.inspect(sage,mf);int title=HttpUtil.optionalInt(request.getParameter("title"),0,"title");
            StringBuilder out=new StringBuilder("{\"id\":").append(id).append(",\"disc\":").append(d.json());
            if(title>0){DiscTitleProbe.Title t=DiscTitleProbe.probe(d.source.videoTs,title,d.capability);out.append(",\"titleProbe\":").append(t.json()).append(",\"movieOnlyPlan\":").append(DiscTitleProbe.movieOnlyPlanJson(d.source,d.capability,title,t));}
            out.append('}');response.getWriter().write(out.toString());
        }catch(IllegalArgumentException e){HttpUtil.jsonError(response,400,e.getMessage());}catch(RuntimeException e){HttpUtil.jsonError(response,500,e.getMessage());}
    }
}
