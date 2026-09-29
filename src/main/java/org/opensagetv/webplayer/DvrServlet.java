package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** SageTV DVR schedule/conflict view plus explicit schedule cancellation. */
public class DvrServlet extends HttpServlet {
    private static final class Item {
        int airingId;
        int mediaId;
        String title;
        String episode;
        String description;
        String category;
        String channelName;
        String channelNumber;
        long start;
        long end;
        long scheduleStart;
        long scheduleEnd;
        String quality;
        String device;
        boolean manualRecord;
        boolean watched;
        boolean conflict;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            SageApiBridge sage = SageApiBridge.create();
            Map<Integer,String> devices = new HashMap<Integer,String>();
            for (String device : sage.getCaptureDevices()) {
                for (Object airing : sage.getScheduledRecordingsForDevice(device)) {
                    int aid = sage.getAiringId(airing);
                    if (aid > 0) {
                        String oldDevice = devices.get(aid);
                        devices.put(aid, oldDevice == null || oldDevice.isEmpty() ? device : oldDevice + ", " + device);
                    }
                }
            }
            List<Item> scheduled = new ArrayList<Item>();
            for (Object airing : sage.getScheduledRecordings()) {
                Item item = item(sage, airing, false);
                if (item != null) { item.device = devices.containsKey(item.airingId) ? devices.get(item.airingId) : ""; scheduled.add(item); }
            }
            List<Item> conflicts = new ArrayList<Item>();
            for (Object airing : sage.getAiringsThatWontBeRecorded(true)) {
                Item item = item(sage, airing, true);
                if (item != null) conflicts.add(item);
            }
            Comparator<Item> byStart = new Comparator<Item>() {
                @Override public int compare(Item a, Item b) { return Long.compare(a.start, b.start); }
            };
            Collections.sort(scheduled, byStart);
            Collections.sort(conflicts, byStart);

            StringBuilder out = new StringBuilder(12000);
            out.append("{\"scheduledCount\":").append(scheduled.size())
               .append(",\"conflictCount\":").append(conflicts.size())
               .append(",\"scheduled\":[");
            appendList(out, scheduled);
            out.append("],\"conflicts\":[");
            appendList(out, conflicts);
            out.append("]}");
            response.getWriter().write(out.toString());
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
            int airingId = HttpUtil.requiredInt(request.getParameter("airingId"), "airingId");
            String action = request.getParameter("action");
            if (action == null) action = "cancel";
            if (!"cancel".equalsIgnoreCase(action)) {
                HttpUtil.jsonError(response, 400, "Unsupported DVR action: " + action);
                return;
            }
            SageApiBridge sage = SageApiBridge.create();
            Object airing = sage.getAiringForId(airingId);
            if (airing == null) {
                HttpUtil.jsonError(response, 404, "Airing not found: " + airingId);
                return;
            }
            sage.cancelRecord(airing);
            response.getWriter().write("{\"ok\":true,\"action\":\"cancel\",\"airingId\":" + airingId + "}");
        } catch (IllegalArgumentException e) {
            HttpUtil.jsonError(response, 400, e.getMessage());
        } catch (RuntimeException e) {
            HttpUtil.jsonError(response, 500, e.getMessage());
        }
    }

    private static Item item(SageApiBridge sage, Object airing, boolean conflict) {
        if (airing == null) return null;
        Item x = new Item();
        x.airingId = sage.getAiringId(airing);
        if (x.airingId <= 0) return null;
        Object mf = sage.getMediaFileForAiring(airing);
        x.mediaId = mf == null ? 0 : sage.getMediaFileId(mf);
        Object show = sage.getShow(airing);
        x.title = sage.getAiringTitle(airing);
        x.episode = show == null ? "" : sage.getShowEpisode(show);
        x.description = show == null ? "" : truncate(sage.getShowDescription(show), 500);
        x.category = show == null ? "" : sage.getShowCategory(show);
        x.channelName = sage.getAiringChannelName(airing);
        x.channelNumber = sage.getAiringChannelNumber(airing);
        x.start = sage.getAiringStartTime(airing);
        x.end = sage.getAiringEndTime(airing);
        x.scheduleStart = sage.getScheduleStartTime(airing);
        x.scheduleEnd = sage.getScheduleEndTime(airing);
        x.quality = sage.getRecordingQuality(airing);
        x.device = "";
        x.manualRecord = sage.isManualRecord(airing);
        x.watched = sage.isWatched(airing);
        x.conflict = conflict;
        return x;
    }

    private static void appendList(StringBuilder out, List<Item> items) {
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
               .append("\"start\":").append(x.start).append(',')
               .append("\"end\":").append(x.end).append(',')
               .append("\"scheduleStart\":").append(x.scheduleStart).append(',')
               .append("\"scheduleEnd\":").append(x.scheduleEnd).append(',')
               .append("\"quality\":\"").append(HttpUtil.json(x.quality)).append("\",")
               .append("\"device\":\"").append(HttpUtil.json(x.device)).append("\",")
               .append("\"manualRecord\":").append(x.manualRecord).append(',')
               .append("\"watched\":").append(x.watched).append(',')
               .append("\"conflict\":").append(x.conflict)
               .append('}');
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
