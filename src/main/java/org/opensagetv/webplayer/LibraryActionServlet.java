package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Explicit SageTV library mutations. All operations are scoped to a SageTV MediaFile ID. */
public class LibraryActionServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            int id = HttpUtil.requiredInt(request.getParameter("id"), "id");
            SageApiBridge sage = SageApiBridge.create();
            Object mf = sage.getMediaFile(id);
            if (mf == null) { HttpUtil.jsonError(response, 404, "MediaFile not found: " + id); return; }
            Object airing = sage.getMediaFileAiring(mf);
            long fileStart = sage.getFileStartTime(mf);
            long latest = airing == null ? 0L : sage.getLatestWatchedTime(airing);
            double resume = latest > fileStart && fileStart > 0 ? (latest - fileStart) / 1000.0 : 0.0;
            response.getWriter().write("{\"id\":" + id +
                ",\"watched\":" + (airing != null && sage.isWatched(airing)) +
                ",\"watchedCompletely\":" + (airing != null && sage.isWatchedCompletely(airing)) +
                ",\"serverResumeSeconds\":" + String.format(java.util.Locale.US, "%.3f", Math.max(0.0, resume)) +
                ",\"recording\":" + sage.isFileCurrentlyRecording(mf) + "}");
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
            int id = HttpUtil.requiredInt(request.getParameter("id"), "id");
            String action = request.getParameter("action");
            if (action == null || action.trim().isEmpty()) throw new IllegalArgumentException("Missing query parameter: action");
            SageApiBridge sage = SageApiBridge.create();
            Object mf = sage.getMediaFile(id);
            if (mf == null) { HttpUtil.jsonError(response, 404, "MediaFile not found: " + id); return; }
            Object airing = sage.getMediaFileAiring(mf);

            if ("watched".equalsIgnoreCase(action)) {
                if (airing == null) { HttpUtil.jsonError(response, 409, "MediaFile has no Airing"); return; }
                sage.setWatched(airing);
                ok(response, action, id);
                return;
            }
            if ("unwatched".equalsIgnoreCase(action)) {
                if (airing == null) { HttpUtil.jsonError(response, 409, "MediaFile has no Airing"); return; }
                sage.clearWatched(airing);
                ok(response, action, id);
                return;
            }
            if ("progress".equalsIgnoreCase(action)) {
                if (airing == null) { HttpUtil.jsonError(response, 409, "MediaFile has no Airing"); return; }
                if (sage.isFileCurrentlyRecording(mf)) { HttpUtil.jsonError(response, 409, "Progress sync is disabled for active recordings"); return; }
                double seconds = optionalDouble(request.getParameter("seconds"), 0.0);
                long fileStart = sage.getFileStartTime(mf);
                if (fileStart <= 0L) fileStart = sage.getAiringStartTime(airing);
                long watchedEnd = fileStart + Math.max(0L, Math.round(seconds * 1000.0));
                long airingEnd = sage.getAiringEndTime(airing);
                if (airingEnd > 0L) watchedEnd = Math.min(watchedEnd, airingEnd);
                sage.setWatchedTimes(airing, watchedEnd, System.currentTimeMillis() - Math.max(0L, Math.round(seconds * 1000.0)));
                response.getWriter().write("{\"ok\":true,\"action\":\"progress\",\"id\":" + id + ",\"seconds\":" + String.format(java.util.Locale.US, "%.3f", seconds) + "}");
                return;
            }
            if ("delete".equalsIgnoreCase(action) || "deleteWithoutPrejudice".equalsIgnoreCase(action)) {
                if (!"DELETE".equals(request.getParameter("confirm"))) {
                    HttpUtil.jsonError(response, 400, "Deletion requires confirm=DELETE");
                    return;
                }
                if (sage.isFileCurrentlyRecording(mf)) {
                    HttpUtil.jsonError(response, 409, "Cannot delete a recording that is still active");
                    return;
                }
                boolean deleted = "deleteWithoutPrejudice".equalsIgnoreCase(action)
                    ? sage.deleteFileWithoutPrejudice(mf) : sage.deleteFile(mf);
                if (!deleted) { HttpUtil.jsonError(response, 409, "SageTV did not delete the MediaFile"); return; }
                ok(response, action, id);
                return;
            }
            HttpUtil.jsonError(response, 400, "Unsupported library action: " + action);
        } catch (IllegalArgumentException e) {
            HttpUtil.jsonError(response, 400, e.getMessage());
        } catch (RuntimeException e) {
            HttpUtil.jsonError(response, 500, e.getMessage());
        }
    }

    private static void ok(HttpServletResponse response, String action, int id) throws IOException {
        response.getWriter().write("{\"ok\":true,\"action\":\"" + HttpUtil.json(action) + "\",\"id\":" + id + "}");
    }

    private static double optionalDouble(String value, double fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number: " + value); }
    }
}
