package org.opensagetv.webplayer;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class StreamServlet extends HttpServlet {
    private static final int BUFFER = 256 * 1024;

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, true);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, false);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response, boolean headOnly) throws IOException {
        try {
            int id = HttpUtil.requiredInt(request.getParameter("id"), "id");
            int segment = HttpUtil.optionalInt(request.getParameter("segment"), 0, "segment");
            if (segment < 0) throw new IllegalArgumentException("segment must be >= 0");

            SageApiBridge sage = SageApiBridge.create();
            Object mediaFile = sage.getMediaFile(id);
            if (mediaFile == null) {
                response.sendError(404, "SageTV MediaFile not found");
                return;
            }
            int count = sage.getNumberOfSegments(mediaFile);
            if (segment >= count) {
                response.sendError(404, "Segment does not exist");
                return;
            }
            File file = sage.getFileForSegment(mediaFile, segment);
            if (file == null) {
                response.sendError(404, "SageTV returned no physical file for this media segment");
                return;
            }

            try (SeekableMediaSource source = MediaSourceFactory.open(sage, file)) {
                long length = source.length();
                response.setHeader("Accept-Ranges", "bytes");
                response.setHeader("Cache-Control", "no-store");
                response.setContentType(contentType(file.getName()));
                response.setHeader("Content-Disposition", "inline; filename=\"" + safeFilename(file.getName()) + "\"");
                response.setHeader("X-SageTV-Stream-Source", source.mode());
                response.setHeader("X-SageTV-Stream-Detail", asciiHeader(source.detail()));
                response.setHeader("X-SageTV-Playback-Mode", "ORIGINAL_DIRECT");

                ByteRange range;
                try {
                    range = ByteRange.parseSingle(request.getHeader("Range"), length);
                } catch (IllegalArgumentException e) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + length);
                    return;
                }

                long start = range == null ? 0 : range.start;
                long end = range == null ? Math.max(0, length - 1) : range.end;
                long sendLength = length == 0 ? 0 : end - start + 1;

                if (range != null) {
                    response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                    response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
                }
                response.setHeader("Content-Length", Long.toString(sendLength));
                response.setHeader("X-SageTV-Recording", Boolean.toString(sage.isFileCurrentlyRecording(mediaFile)));
                response.setHeader("X-SageTV-MediaFile-ID", Integer.toString(id));
                response.setHeader("X-SageTV-Segment", Integer.toString(segment));

                if (headOnly || sendLength == 0) return;

                source.seek(start);
                ServletOutputStream output = response.getOutputStream();
                byte[] buffer = new byte[BUFFER];
                long remaining = sendLength;
                while (remaining > 0) {
                    int requested = (int) Math.min(buffer.length, remaining);
                    int read = source.read(buffer, 0, requested);
                    if (read < 0) break;
                    if (read == 0) continue;
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
            } catch (IOException e) {
                if (!response.isCommitted()) {
                    response.sendError(404, e.getMessage());
                } else {
                    throw e;
                }
            }
        } catch (IllegalArgumentException e) {
            response.sendError(400, e.getMessage());
        } catch (RuntimeException e) {
            response.sendError(500, e.getMessage());
        }
    }

    private static String contentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".ts") || lower.endsWith(".m2ts") || lower.endsWith(".mts")) return "video/mp2t";
        if (lower.endsWith(".mpg") || lower.endsWith(".mpeg")) return "video/mpeg";
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        return "application/octet-stream";
    }

    private static String safeFilename(String name) {
        return name.replace("\\", "_").replace("/", "_").replace("\"", "_");
    }

    private static String asciiHeader(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 32 && c <= 126) out.append(c);
            else out.append('?');
        }
        return out.toString();
    }
}
