package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecordingsServlet extends HttpServlet {
    private static final class Item {
        int id;
        String title;
        String episode;
        String description;
        String channelName;
        String channelNumber;
        String category;
        String year;
        boolean watched;
        boolean watchedCompletely;
        double serverResumeSeconds;
        boolean recording;
        long start;
        long duration;
        String format;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            int limit = HttpUtil.optionalInt(request.getParameter("limit"), 100, "limit");
            limit = Math.max(1, Math.min(500, limit));
            String q = request.getParameter("q");
            q = q == null ? "" : q.trim().toLowerCase();
            boolean activeOnly = "true".equalsIgnoreCase(request.getParameter("active"));

            SageApiBridge sage = SageApiBridge.create();
            List<Item> items = new ArrayList<Item>();
            for (Object mf : sage.getMediaFiles()) {
                if (mf == null || !sage.isTVFile(mf)) continue;
                boolean recording = sage.isFileCurrentlyRecording(mf);
                if (activeOnly && !recording) continue;

                String title = sage.getMediaTitle(mf);
                Object airing = sage.getMediaFileAiring(mf);
                Object show = airing == null ? null : sage.getShow(airing);
                String episode = show == null ? "" : sage.getShowEpisode(show);
                String description = show == null ? "" : sage.getShowDescription(show);
                String category = show == null ? "" : sage.getShowCategory(show);
                String year = show == null ? "" : sage.getShowYear(show);
                String haystack = ((title == null ? "" : title) + " " + episode + " " + description + " " + category + " " + year).toLowerCase();
                if (!q.isEmpty() && !haystack.contains(q)) continue;

                Item item = new Item();
                item.id = sage.getMediaFileId(mf);
                item.title = title == null ? "" : title;
                item.episode = episode;
                item.description = truncate(description, 700);
                item.recording = recording;
                item.start = airing == null ? 0L : sage.getAiringStartTime(airing);
                item.duration = airing == null ? 0L : sage.getAiringDuration(airing);
                item.channelName = airing == null ? "" : sage.getAiringChannelName(airing);
                item.channelNumber = airing == null ? "" : sage.getAiringChannelNumber(airing);
                item.category = category;
                item.year = year;
                item.watched = airing != null && sage.isWatched(airing);
                item.watchedCompletely = airing != null && sage.isWatchedCompletely(airing);
                long fileStart = sage.getFileStartTime(mf);
                long latestWatched = airing == null ? 0L : sage.getLatestWatchedTime(airing);
                item.serverResumeSeconds = latestWatched > fileStart && fileStart > 0 ? (latestWatched - fileStart) / 1000.0 : 0.0;
                item.format = sage.getMediaFileFormatDescription(mf);
                items.add(item);
            }
            Collections.sort(items, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) { return Long.compare(b.start, a.start); }
            });
            if (items.size() > limit) items = items.subList(0, limit);

            StringBuilder out = new StringBuilder(16384);
            out.append("{\"count\":").append(items.size()).append(",\"recordings\":[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) out.append(',');
                Item x = items.get(i);
                out.append('{')
                   .append("\"id\":").append(x.id).append(',')
                   .append("\"title\":\"").append(HttpUtil.json(x.title)).append("\",")
                   .append("\"episode\":\"").append(HttpUtil.json(x.episode)).append("\",")
                   .append("\"description\":\"").append(HttpUtil.json(x.description)).append("\",")
                   .append("\"channelName\":\"").append(HttpUtil.json(x.channelName)).append("\",")
                   .append("\"channelNumber\":\"").append(HttpUtil.json(x.channelNumber)).append("\",")
                   .append("\"category\":\"").append(HttpUtil.json(x.category)).append("\",")
                   .append("\"year\":\"").append(HttpUtil.json(x.year)).append("\",")
                   .append("\"watched\":").append(x.watched).append(',')
                   .append("\"watchedCompletely\":").append(x.watchedCompletely).append(',')
                   .append("\"serverResumeSeconds\":").append(String.format(java.util.Locale.US, "%.3f", Math.max(0.0, x.serverResumeSeconds))).append(',')
                   .append("\"thumbnailUrl\":\"/sagex/media/thumbnail/").append(x.id).append("?scalex=320&scaley=180\",")
                   .append("\"recording\":").append(x.recording).append(',')
                   .append("\"start\":").append(x.start).append(',')
                   .append("\"duration\":").append(x.duration).append(',')
                   .append("\"format\":\"").append(HttpUtil.json(x.format)).append("\"")
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

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
