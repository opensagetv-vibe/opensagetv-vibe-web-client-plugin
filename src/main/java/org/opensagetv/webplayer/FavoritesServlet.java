package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** SageTV Favorite recording rules: list, create and safely edit core scheduling options. */
public class FavoritesServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prep(response);
        try {
            SageApiBridge sage = SageApiBridge.create();
            Object[] favorites = sage.sort(sage.getFavorites(), false, "FavoritePriority");
            StringBuilder out = new StringBuilder(16384);
            out.append("{\"count\":").append(favorites.length).append(",\"favorites\":[");
            for (int i=0;i<favorites.length;i++) {
                if (i>0) out.append(',');
                appendFavorite(out, sage, favorites[i]);
            }
            out.append("]}");
            response.getWriter().write(out.toString());
        } catch (RuntimeException e) { HttpUtil.jsonError(response, 500, e.getMessage()); }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prep(response);
        try {
            SageApiBridge sage = SageApiBridge.create();
            String action = str(request.getParameter("action"));
            if ("add".equalsIgnoreCase(action)) {
                String title = str(request.getParameter("title"));
                if (title.isEmpty()) { HttpUtil.jsonError(response, 400, "Favorite title is required"); return; }
                Object f = sage.addFavorite(title, bool(request,"firstRuns",true), bool(request,"reRuns",true),
                    str(request.getParameter("category")), str(request.getParameter("subCategory")),
                    str(request.getParameter("network")), str(request.getParameter("channel")),
                    str(request.getParameter("timeslot")), str(request.getParameter("keyword")));
                if (f == null) { HttpUtil.jsonError(response, 409, "SageTV did not create the Favorite (it may duplicate an existing rule)"); return; }
                response.getWriter().write("{\"ok\":true,\"action\":\"add\",\"favoriteId\":"+sage.getFavoriteId(f)+"}");
                return;
            }
            int id = HttpUtil.requiredInt(request.getParameter("favoriteId"), "favoriteId");
            Object f = sage.getFavoriteForId(id);
            if (f == null) { HttpUtil.jsonError(response,404,"Favorite not found: "+id); return; }
            if ("remove".equalsIgnoreCase(action)) {
                if (!"DELETE".equals(request.getParameter("confirm"))) { HttpUtil.jsonError(response,400,"Removing a Favorite requires confirm=DELETE"); return; }
                sage.removeFavorite(f); ok(response,action,id); return;
            }
            if ("enabled".equalsIgnoreCase(action)) { sage.setFavoriteEnabled(f,bool(request,"enabled",true)); ok(response,action,id); return; }
            if ("padding".equalsIgnoreCase(action)) {
                long startSec = boundedLong(request.getParameter("startSeconds"), -3600, 21600, "startSeconds");
                long stopSec = boundedLong(request.getParameter("stopSeconds"), -3600, 21600, "stopSeconds");
                sage.setStartPadding(f,startSec*1000L); sage.setStopPadding(f,stopSec*1000L); ok(response,action,id); return;
            }
            if ("quality".equalsIgnoreCase(action)) { sage.setFavoriteQuality(f,str(request.getParameter("quality"))); ok(response,action,id); return; }
            if ("keep".equalsIgnoreCase(action)) {
                int count = HttpUtil.optionalInt(request.getParameter("keepAtMost"),0,"keepAtMost");
                if (count<0 || count>999) { HttpUtil.jsonError(response,400,"keepAtMost must be 0..999"); return; }
                sage.setKeepAtMost(f,count); sage.setFavoriteAutoDelete(f,bool(request,"autoDelete",true)); ok(response,action,id); return;
            }
            if ("runs".equalsIgnoreCase(action)) {
                boolean first=bool(request,"firstRuns",true), reruns=bool(request,"reRuns",true);
                if (!sage.setRunStatus(f,first,reruns)) { HttpUtil.jsonError(response,409,"SageTV rejected the run-status change, possibly because it would duplicate another Favorite"); return; }
                ok(response,action,id); return;
            }
            if ("priority".equalsIgnoreCase(action)) {
                int otherId = HttpUtil.requiredInt(request.getParameter("otherFavoriteId"), "otherFavoriteId");
                Object other = sage.getFavoriteForId(otherId);
                if (other == null) { HttpUtil.jsonError(response,404,"Other Favorite not found: "+otherId); return; }
                boolean higher = bool(request,"higher",true);
                if (higher) sage.createFavoritePriority(f, other); else sage.createFavoritePriority(other, f);
                ok(response,action,id); return;
            }
            HttpUtil.jsonError(response,400,"Unsupported Favorite action: "+action);
        } catch (IllegalArgumentException e) { HttpUtil.jsonError(response,400,e.getMessage()); }
          catch (RuntimeException e) { HttpUtil.jsonError(response,500,e.getMessage()); }
    }

    private static void appendFavorite(StringBuilder out, SageApiBridge s, Object f) {
        out.append('{')
           .append("\"id\":").append(s.getFavoriteId(f)).append(',')
           .append("\"title\":\"").append(HttpUtil.json(s.getFavoriteTitle(f))).append("\",")
           .append("\"description\":\"").append(HttpUtil.json(s.getFavoriteDescription(f))).append("\",")
           .append("\"category\":\"").append(HttpUtil.json(s.getFavoriteCategory(f))).append("\",")
           .append("\"subCategory\":\"").append(HttpUtil.json(s.getFavoriteSubCategory(f))).append("\",")
           .append("\"channel\":\"").append(HttpUtil.json(s.getFavoriteChannel(f))).append("\",")
           .append("\"network\":\"").append(HttpUtil.json(s.getFavoriteNetwork(f))).append("\",")
           .append("\"keyword\":\"").append(HttpUtil.json(s.getFavoriteKeyword(f))).append("\",")
           .append("\"timeslot\":\"").append(HttpUtil.json(s.getFavoriteTimeslot(f))).append("\",")
           .append("\"startPaddingSeconds\":").append(s.getStartPadding(f)/1000L).append(',')
           .append("\"stopPaddingSeconds\":").append(s.getStopPadding(f)/1000L).append(',')
           .append("\"quality\":\"").append(HttpUtil.json(s.getFavoriteQuality(f))).append("\",")
           .append("\"keepAtMost\":").append(s.getKeepAtMost(f)).append(',')
           .append("\"autoDelete\":").append(s.isAutoDelete(f)).append(',')
           .append("\"enabled\":").append(s.isFavoriteEnabled(f)).append(',')
           .append("\"firstRuns\":").append(s.isFirstRuns(f)).append(',')
           .append("\"reRuns\":").append(s.isReRuns(f))
           .append('}');
    }
    private static void prep(HttpServletResponse r){r.setCharacterEncoding("UTF-8");r.setContentType("application/json");r.setHeader("Cache-Control","no-store");}
    private static void ok(HttpServletResponse r,String a,int id)throws IOException{r.getWriter().write("{\"ok\":true,\"action\":\""+HttpUtil.json(a)+"\",\"favoriteId\":"+id+"}");}
    private static String str(String s){return s==null?"":s.trim();}
    private static boolean bool(HttpServletRequest r,String n,boolean d){String s=r.getParameter(n);return s==null?d:("1".equals(s)||"true".equalsIgnoreCase(s)||"yes".equalsIgnoreCase(s));}
    private static long boundedLong(String s,long min,long max,String name){if(s==null||s.trim().isEmpty())return 0;try{long v=Long.parseLong(s);if(v<min||v>max)throw new IllegalArgumentException(name+" must be "+min+".."+max);return v;}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid number for "+name+": "+s);}}
}
