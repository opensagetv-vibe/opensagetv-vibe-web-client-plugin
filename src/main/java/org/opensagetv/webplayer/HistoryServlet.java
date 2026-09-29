package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Server-side SageTV recently-watched history. */
public class HistoryServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException{
        response.setCharacterEncoding("UTF-8");response.setContentType("application/json");response.setHeader("Cache-Control","no-store");
        try{
            int days=HttpUtil.optionalInt(request.getParameter("days"),30,"days");days=Math.max(1,Math.min(365,days));
            SageApiBridge s=SageApiBridge.create();Object[] airings=s.getRecentlyWatched(days*86400000L);
            StringBuilder out=new StringBuilder(8192);out.append("{\"days\":").append(days).append(",\"count\":").append(airings.length).append(",\"items\":[");
            for(int i=0;i<airings.length;i++){if(i>0)out.append(',');Object a=airings[i];Object mf=s.getMediaFileForAiring(a);Object show=s.getShow(a);int mid=mf==null?0:s.getMediaFileId(mf);
                out.append('{').append("\"airingId\":").append(s.getAiringId(a)).append(',').append("\"mediaId\":").append(mid).append(',')
                   .append("\"title\":\"").append(HttpUtil.json(s.getAiringTitle(a))).append("\",")
                   .append("\"episode\":\"").append(HttpUtil.json(show==null?"":s.getShowEpisode(show))).append("\",")
                   .append("\"channelName\":\"").append(HttpUtil.json(s.getAiringChannelName(a))).append("\",")
                   .append("\"channelNumber\":\"").append(HttpUtil.json(s.getAiringChannelNumber(a))).append("\",")
                   .append("\"start\":").append(s.getAiringStartTime(a)).append(',')
                   .append("\"resumeSeconds\":").append(mf==null?0.0:Math.max(0.0,(s.getLatestWatchedTime(a)-s.getFileStartTime(mf))/1000.0)).append(',')
                   .append("\"watched\":").append(s.isWatched(a)).append(',')
                   .append("\"thumbnailUrl\":\"").append(mid>0?"/sagex/media/thumbnail/"+mid+"?scalex=320&scaley=180":"").append("\"").append('}');
            }
            out.append("]}");response.getWriter().write(out.toString());
        }catch(IllegalArgumentException e){HttpUtil.jsonError(response,400,e.getMessage());}catch(RuntimeException e){HttpUtil.jsonError(response,500,e.getMessage());}
    }
}
