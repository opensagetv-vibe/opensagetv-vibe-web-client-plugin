package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lightweight SageTV EPG/recording bridge. No arbitrary channel tuning is performed. */
public class GuideServlet extends HttpServlet {
    private static final long HOUR = 60L * 60L * 1000L;

    private static final class Item {
        int airingId;
        int mediaId;
        String title = "";
        String episode = "";
        String description = "";
        String category = "";
        String channelName = "";
        String channelNumber = "";
        String channelLogoUrl = "";
        long start;
        long end;
        boolean manualRecord;
        boolean currentlyAiring;
        boolean watched;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            long now = System.currentTimeMillis();
            long start = optionalLong(request.getParameter("start"), now - 30L * 60L * 1000L);
            int hours = HttpUtil.optionalInt(request.getParameter("hours"), 4, "hours");
            hours = Math.max(1, Math.min(12, hours));
            long end = start + hours * HOUR;
            String q = request.getParameter("q");
            q = q == null ? "" : q.trim().toLowerCase();

            SageApiBridge sage = SageApiBridge.create();
            List<Item> items = new ArrayList<Item>();
            for (Object airing : sage.getAiringsOnViewableChannelsAtTime(start, end, false)) {
                if (airing == null) continue;
                Item x = new Item();
                x.airingId = sage.getAiringId(airing);
                x.title = sage.getAiringTitle(airing);
                x.start = sage.getAiringStartTime(airing);
                x.end = sage.getAiringEndTime(airing);
                if (x.end <= x.start) x.end = x.start + sage.getAiringDuration(airing);
                x.channelName = sage.getAiringChannelName(airing);
                x.channelNumber = sage.getAiringChannelNumber(airing);
                x.channelLogoUrl = x.channelName.isEmpty() ? "" : "/sagex/media/logo/" + urlEncode(x.channelName) + "?scalex=96&scaley=54";
                x.manualRecord = sage.isManualRecord(airing);
                x.currentlyAiring = now >= x.start && now < x.end;
                x.watched = sage.isWatched(airing);
                Object show = sage.getShow(airing);
                if (show != null) {
                    x.episode = sage.getShowEpisode(show);
                    x.description = truncate(sage.getShowDescription(show), 500);
                    x.category = sage.getShowCategory(show);
                }
                Object mf = sage.getMediaFileForAiring(airing);
                if (mf != null) x.mediaId = sage.getMediaFileId(mf);
                String hay = (x.title + " " + x.episode + " " + x.description + " " + x.category + " " + x.channelName + " " + x.channelNumber).toLowerCase();
                if (!q.isEmpty() && !hay.contains(q)) continue;
                items.add(x);
            }
            Collections.sort(items, new Comparator<Item>() {
                @Override public int compare(Item a, Item b) {
                    int c = compareChannel(a.channelNumber, b.channelNumber);
                    if (c != 0) return c;
                    c = a.channelName.compareToIgnoreCase(b.channelName);
                    if (c != 0) return c;
                    return Long.compare(a.start, b.start);
                }
            });

            StringBuilder out = new StringBuilder(Math.max(4096, items.size() * 320));
            out.append("{\"now\":").append(now)
               .append(",\"start\":").append(start)
               .append(",\"end\":").append(end)
               .append(",\"count\":").append(items.size())
               .append(",\"airings\":[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) out.append(',');
                Item x = items.get(i);
                out.append('{')
                   .append("\"airingId\":").append(x.airingId).append(',')
                   .append("\"mediaId\":").append(x.mediaId).append(',')
                   .append("\"title\":\"").append(HttpUtil.json(x.title)).append("\",")
                   .append("\"episode\":\"").append(HttpUtil.json(x.episode)).append("\",")
                   .append("\"description\":\"").append(HttpUtil.json(x.description)).append("\",")
                   .append("\"category\":\"").append(HttpUtil.json(x.category)).append("\",")
                   .append("\"channelName\":\"").append(HttpUtil.json(x.channelName)).append("\",")
                   .append("\"channelNumber\":\"").append(HttpUtil.json(x.channelNumber)).append("\",")
                   .append("\"channelLogoUrl\":\"").append(HttpUtil.json(x.channelLogoUrl)).append("\",")
                   .append("\"start\":").append(x.start).append(',')
                   .append("\"end\":").append(x.end).append(',')
                   .append("\"manualRecord\":").append(x.manualRecord).append(',')
                   .append("\"currentlyAiring\":").append(x.currentlyAiring).append(',')
                   .append("\"watched\":").append(x.watched)
                   .append('}');
            }
            out.append("]}");
            response.getWriter().write(out.toString());
        } catch (IllegalArgumentException e) {
            HttpUtil.jsonError(response, 400, e.getMessage());
        } catch (RuntimeException e) {
            HttpUtil.jsonError(response, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            int id = HttpUtil.requiredInt(request.getParameter("airingId"), "airingId");
            String action = request.getParameter("action");
            if (action == null) action = "record";
            SageApiBridge sage = SageApiBridge.create();
            Object airing = sage.getAiringForId(id);
            if (airing == null) { HttpUtil.jsonError(response, 404, "Airing not found: " + id); return; }

            if ("cancel".equalsIgnoreCase(action)) {
                sage.cancelRecord(airing);
                response.getWriter().write("{\"ok\":true,\"action\":\"cancel\",\"airingId\":" + id + "}");
                return;
            }
            if (!"record".equalsIgnoreCase(action) && !"watch".equalsIgnoreCase(action)) {
                HttpUtil.jsonError(response, 400, "Unsupported action: " + action); return;
            }

            if ("watch".equalsIgnoreCase(action)) {
                long now = System.currentTimeMillis();
                long aStart = sage.getAiringStartTime(airing);
                long aEnd = sage.getAiringEndTime(airing);
                if (!(now >= aStart && now < aEnd)) { HttpUtil.jsonError(response, 409, "The requested airing is not currently live."); return; }
            }

            Object result = Boolean.TRUE;
            if (!sage.isManualRecord(airing) && sage.getMediaFileForAiring(airing) == null) result = sage.recordAiring(airing);
            boolean ok = result instanceof Boolean ? ((Boolean) result) : "true".equalsIgnoreCase(String.valueOf(result));
            if (!ok) { HttpUtil.jsonError(response, 409, String.valueOf(result)); return; }

            int mediaId = 0;
            if ("watch".equalsIgnoreCase(action)) {
                for (int i = 0; i < 30 && mediaId == 0; i++) {
                    Object mf = sage.getMediaFileForAiring(airing);
                    if (mf != null) mediaId = sage.getMediaFileId(mf);
                    if (mediaId == 0) try { Thread.sleep(200L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            } else {
                Object mf = sage.getMediaFileForAiring(airing);
                if (mf != null) mediaId = sage.getMediaFileId(mf);
            }
            response.getWriter().write("{\"ok\":true,\"action\":\"" + HttpUtil.json(action.toLowerCase()) + "\",\"airingId\":" + id + ",\"mediaId\":" + mediaId + ",\"pending\":" + ("watch".equalsIgnoreCase(action) && mediaId == 0) + "}");
        } catch (IllegalArgumentException e) {
            HttpUtil.jsonError(response, 400, e.getMessage());
        } catch (RuntimeException e) {
            HttpUtil.jsonError(response, 500, e.getMessage());
        }
    }

    private static String urlEncode(String value) {
        try { return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return ""; }
    }
    private static long optionalLong(String value, long fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid start"); }
    }
    private static String truncate(String s, int max) { if (s == null) return ""; return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
    private static int compareChannel(String a, String b) {
        double da = parseChannel(a), db = parseChannel(b);
        if (!Double.isNaN(da) && !Double.isNaN(db)) { int c = Double.compare(da, db); if (c != 0) return c; }
        return (a == null ? "" : a).compareToIgnoreCase(b == null ? "" : b);
    }
    private static double parseChannel(String s) {
        if (s == null) return Double.NaN;
        try { return Double.parseDouble(s.replace('-', '.')); } catch (Exception e) { return Double.NaN; }
    }
}
