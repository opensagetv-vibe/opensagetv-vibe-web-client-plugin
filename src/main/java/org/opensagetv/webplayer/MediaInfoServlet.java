package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class MediaInfoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            int id = HttpUtil.requiredInt(request.getParameter("id"), "id");
            SageApiBridge sage = SageApiBridge.create();
            Object mediaFile = sage.getMediaFile(id);
            if (mediaFile == null) {
                HttpUtil.jsonError(response, 404, "SageTV MediaFile not found: " + id);
                return;
            }

            boolean recording = sage.isFileCurrentlyRecording(mediaFile);
            Object airing = sage.getMediaFileAiring(mediaFile);
            Object show = airing == null ? null : sage.getShow(airing);
            int count = sage.getNumberOfSegments(mediaFile);
            long fileStart = sage.getFileStartTime(mediaFile);
            long latestWatched = airing == null ? 0L : sage.getLatestWatchedTime(airing);
            double serverResumeSeconds = latestWatched > fileStart && fileStart > 0 ? (latestWatched - fileStart) / 1000.0 : 0.0;
            DiscSourceInfo discSource = DiscSourceInfo.inspect(sage, mediaFile);
            String discJson = discSource.isDisc() ? DiscInspection.inspect(sage, mediaFile).json() : ("{\"source\":" + discSource.json() + ",\"metadata\":" + new DiscMetadataHealth(sage, mediaFile).json() + "}");
            StringBuilder json = new StringBuilder(5000);
            json.append('{')
                .append("\"id\":").append(id).append(',')
                .append("\"title\":\"").append(HttpUtil.json(sage.getMediaTitle(mediaFile))).append("\",")
                .append("\"episode\":\"").append(HttpUtil.json(show == null ? "" : sage.getShowEpisode(show))).append("\",")
                .append("\"description\":\"").append(HttpUtil.json(show == null ? "" : sage.getShowDescription(show))).append("\",")
                .append("\"category\":\"").append(HttpUtil.json(show == null ? "" : sage.getShowCategory(show))).append("\",")
                .append("\"year\":\"").append(HttpUtil.json(show == null ? "" : sage.getShowYear(show))).append("\",")
                .append("\"watched\":").append(airing != null && sage.isWatched(airing)).append(',')
                .append("\"watchedCompletely\":").append(airing != null && sage.isWatchedCompletely(airing)).append(',')
                .append("\"serverResumeSeconds\":").append(String.format(java.util.Locale.US, "%.3f", Math.max(0.0, serverResumeSeconds))).append(',')
                .append("\"thumbnailUrl\":\"/sagex/media/thumbnail/").append(id).append("?scalex=640&scaley=360\",")
                .append("\"channelName\":\"").append(HttpUtil.json(airing == null ? "" : sage.getAiringChannelName(airing))).append("\",")
                .append("\"channelNumber\":\"").append(HttpUtil.json(airing == null ? "" : sage.getAiringChannelNumber(airing))).append("\",")
                .append("\"start\":").append(airing == null ? 0L : sage.getAiringStartTime(airing)).append(',')
                .append("\"duration\":").append(airing == null ? 0L : sage.getAiringDuration(airing)).append(',')
                .append("\"recording\":").append(recording).append(',')
                .append("\"serverAddress\":\"").append(HttpUtil.json(sage.getServerAddress())).append("\",")
                .append("\"encoding\":\"").append(HttpUtil.json(sage.getMediaFileEncoding(mediaFile))).append("\",")
                .append("\"format\":\"").append(HttpUtil.json(sage.getMediaFileFormatDescription(mediaFile))).append("\",")
                .append("\"segments\":").append(count).append(',')
                .append("\"disc\":").append(discJson).append(',')
                .append("\"playback\":{")
                .append("\"raw\":\"stream.ts?id=").append(id).append("&segment=0\",")
                .append("\"live\":\"live.ts?id=").append(id).append("&start=begin\",")
                .append("\"liveEdge\":\"live.ts?id=").append(id).append("&start=live\",")
                .append("\"transcode\":\"transcode.mp4?id=").append(id).append("&segment=0\"")
                .append("},")
                .append("\"files\":[");
            for (int i = 0; i < count; i++) {
                if (i > 0) json.append(',');
                File f = sage.getFileForSegment(mediaFile, i);
                json.append('{').append("\"segment\":").append(i);
                if (f != null) {
                    MediaSourceFactory.Probe probe = MediaSourceFactory.probe(sage, f);
                    json.append(",\"name\":\"").append(HttpUtil.json(f.getName())).append("\"")
                        .append(",\"path\":\"").append(HttpUtil.json(f.getPath())).append("\"")
                        .append(",\"absolutePath\":\"").append(HttpUtil.json(f.getAbsolutePath())).append("\"")
                        .append(",\"exists\":").append(f.exists())
                        .append(",\"isFile\":").append(f.isFile())
                        .append(",\"canRead\":").append(f.canRead())
                        .append(",\"localSize\":").append(f.exists() ? f.length() : 0)
                        .append(",\"streamAccessible\":").append(probe.accessible)
                        .append(",\"streamMode\":\"").append(HttpUtil.json(probe.mode)).append("\"")
                        .append(",\"streamDetail\":\"").append(HttpUtil.json(probe.detail)).append("\"")
                        .append(",\"streamSize\":").append(probe.size);
                    if (probe.error != null) json.append(",\"streamError\":\"").append(HttpUtil.json(probe.error)).append("\"");
                }
                json.append('}');
            }
            json.append("]}");

            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(json.toString());
        } catch (IllegalArgumentException e) {
            HttpUtil.jsonError(response, 400, e.getMessage());
        } catch (RuntimeException e) {
            HttpUtil.jsonError(response, 500, e.getMessage());
        }
    }
}
