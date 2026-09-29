package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Starts/stops/statuses compatibility HLS sessions. */
public class HlsControlServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = value(request.getParameter("action"), "status");
        if ("status".equalsIgnoreCase(action)) {
            HlsSessionManager.Session s = HlsSessionManager.get(request.getParameter("session"));
            if (s == null) { HttpUtil.jsonError(response, 404, "HLS session not found"); return; }
            write(response, HlsSessionManager.json(s));
            return;
        }
        HttpUtil.jsonError(response, 400, "Unsupported HLS action: " + action);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = value(request.getParameter("action"), "start");
        try {
            if ("start".equalsIgnoreCase(action)) {
                int id = HttpUtil.requiredInt(request.getParameter("id"), "id");
                int segment = HttpUtil.optionalInt(request.getParameter("segment"), 0, "segment");
                int audio = HttpUtil.optionalInt(request.getParameter("audio"), 0, "audio");
                double start = optionalDouble(request.getParameter("start"), 0.0, "start");
                HlsSessionManager.Session s = HlsSessionManager.start(id, segment, audio, start);
                write(response, HlsSessionManager.json(s));
                return;
            }
            if ("stop".equalsIgnoreCase(action)) {
                String session = request.getParameter("session");
                boolean stopped = HlsSessionManager.stop(session);
                write(response, "{\"stopped\":" + stopped + "}");
                return;
            }
            HttpUtil.jsonError(response, 400, "Unsupported HLS action: " + action);
        } catch (IllegalArgumentException e) {
            HttpUtil.jsonError(response, 400, e.getMessage());
        } catch (IOException e) {
            HttpUtil.jsonError(response, 502, e.getMessage());
        } catch (RuntimeException e) {
            HttpUtil.jsonError(response, 500, e.getMessage());
        }
    }

    private static void write(HttpServletResponse response, String json) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.getWriter().write(json);
    }

    private static String value(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }
    private static double optionalDouble(String value, double fallback, String name) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try { return Double.parseDouble(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number for " + name + ": " + value); }
    }
}
