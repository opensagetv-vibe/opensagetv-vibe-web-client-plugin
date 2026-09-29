package org.opensagetv.webplayer;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Snapshot-serving endpoint for FFmpeg HLS playlist/segments. */
public class HlsFileServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null) { response.sendError(404); return; }
        String[] parts = path.replaceFirst("^/+", "").split("/");
        if (parts.length != 2) { response.sendError(404); return; }
        String sessionId = parts[0];
        String filename = parts[1];
        try {
            // The initial manifest request is allowed to wait for FFmpeg startup. Segment requests
            // wait briefly for temp_file atomic rename to become visible.
            File file = HlsSessionManager.waitForFile(sessionId, filename,
                    "stream.m3u8".equals(filename) ? 16000L : 5000L);
            if (!file.isFile() || file.length() <= 0) {
                HlsSessionManager.Session s = HlsSessionManager.get(sessionId);
                if (s != null && s.error != null && s.error.length() > 0) response.sendError(502, s.error);
                else response.sendError(404);
                return;
            }
            byte[] data = Files.readAllBytes(file.toPath());
            response.setContentType(filename.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t");
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            response.setHeader("Accept-Ranges", "none");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLength(data.length);
            ServletOutputStream out = response.getOutputStream();
            out.write(data);
            out.flush();
        } catch (IOException e) {
            response.sendError(404, e.getMessage());
        }
    }
}
