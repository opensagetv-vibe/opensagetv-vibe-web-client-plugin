package org.opensagetv.webplayer;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Chunked original-TS stream for a MediaFile that is still recording.
 * Follows file growth and SageTV segment rollovers without modifying Sage.jar.
 */
public class LiveStreamServlet extends HttpServlet {
    private static final int BUFFER = 256 * 1024;
    private static final long POLL_MS = 250;
    private static final long LIVE_EDGE_BACK_BYTES = 8L * 1024L * 1024L;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private int seq;
        @Override public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, "SageTV-WebPlayer-Live-" + (++seq));
            t.setDaemon(true);
            return t;
        }
    });

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final int id;
        final int requestedSegment;
        try {
            id = HttpUtil.requiredInt(request.getParameter("id"), "id");
            requestedSegment = HttpUtil.optionalInt(request.getParameter("segment"), -1, "segment");
        } catch (IllegalArgumentException e) {
            response.sendError(400, e.getMessage());
            return;
        }
        final String startMode = request.getParameter("start") == null ? "begin" : request.getParameter("start").trim().toLowerCase();
        if (!"begin".equals(startMode) && !"live".equals(startMode)) {
            response.sendError(400, "start must be begin or live");
            return;
        }

        response.setContentType("video/mp2t");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-SageTV-MediaFile-ID", Integer.toString(id));
        response.setHeader("X-SageTV-Live", "true");

        final AsyncContext async = request.startAsync();
        async.setTimeout(0);
        EXECUTOR.submit(new Runnable() {
            @Override public void run() {
                try {
                    stream(id, requestedSegment, startMode, (HttpServletResponse) async.getResponse());
                } catch (IOException ignored) {
                    // Most I/O errors here are a browser disconnect/seek. Do not turn a
                    // committed streaming response into a noisy server error.
                } catch (RuntimeException e) {
                    try {
                        HttpServletResponse r = (HttpServletResponse) async.getResponse();
                        if (!r.isCommitted()) r.sendError(500, e.getMessage());
                    } catch (Exception ignored) {}
                } finally {
                    try { async.complete(); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void stream(int id, int requestedSegment, String startMode, HttpServletResponse response) throws IOException {
        SageApiBridge sage = SageApiBridge.create();
        Object mf = sage.getMediaFile(id);
        if (mf == null) {
            response.sendError(404, "SageTV MediaFile not found");
            return;
        }
        int count = sage.getNumberOfSegments(mf);
        if (count <= 0) {
            response.sendError(404, "MediaFile has no physical segments");
            return;
        }

        int segment = requestedSegment >= 0 ? requestedSegment : ("live".equals(startMode) ? count - 1 : 0);
        if (segment < 0 || segment >= count) {
            response.sendError(404, "Requested segment does not exist");
            return;
        }

        ServletOutputStream out = response.getOutputStream();
        byte[] buffer = new byte[BUFFER];
        long offset = 0;
        boolean initial = true;

        while (true) {
            mf = sage.getMediaFile(id);
            if (mf == null) break;
            count = sage.getNumberOfSegments(mf);
            if (segment >= count) {
                if (!sage.isFileCurrentlyRecording(mf)) break;
                sleep();
                continue;
            }
            File file = sage.getFileForSegment(mf, segment);
            if (file == null) {
                if (!sage.isFileCurrentlyRecording(mf)) break;
                sleep();
                continue;
            }

            try (SeekableMediaSource source = MediaSourceFactory.open(sage, file)) {
                response.setHeader("X-SageTV-Stream-Source", source.mode());
                if (initial && "live".equals(startMode)) {
                    long available = source.length();
                    offset = Math.max(0, available - LIVE_EDGE_BACK_BYTES);
                    // MPEG-TS packets are 188 bytes. This does not guarantee PES/keyframe
                    // alignment, but PAT/PMT and video headers repeat and the browser demuxer
                    // can resynchronize quickly.
                    offset -= offset % 188;
                }
                initial = false;

                while (true) {
                    mf = sage.getMediaFile(id);
                    boolean recording = mf != null && sage.isFileCurrentlyRecording(mf);
                    int latestCount = mf == null ? count : sage.getNumberOfSegments(mf);
                    long available = source.length();

                    if (offset < available) {
                        source.seek(offset);
                        int want = (int) Math.min(buffer.length, available - offset);
                        int read;
                        try { read = source.read(buffer, 0, want); }
                        catch (IOException e) {
                            // A concurrently growing source can briefly race SIZE/READ.
                            sleep();
                            continue;
                        }
                        if (read > 0) {
                            out.write(buffer, 0, read);
                            out.flush();
                            offset += read;
                            continue;
                        }
                    }

                    if (segment + 1 < latestCount) {
                        segment++;
                        offset = 0;
                        break;
                    }
                    if (!recording) return;
                    sleep();
                }
            }
        }
    }

    private static void sleep() {
        try { Thread.sleep(POLL_MS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
