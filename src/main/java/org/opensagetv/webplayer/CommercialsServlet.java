package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads Comskip EDL markers adjacent to SageTV-owned MediaFiles. */
public class CommercialsServlet extends HttpServlet {
    private static final class Marker { double start, end; int type; }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            int id = HttpUtil.requiredInt(request.getParameter("id"), "id");
            int segment = HttpUtil.optionalInt(request.getParameter("segment"), 0, "segment");
            SageApiBridge sage = SageApiBridge.create();
            Object mf = sage.getMediaFile(id);
            if (mf == null) { HttpUtil.jsonError(response, 404, "MediaFile not found: " + id); return; }
            if (segment < 0 || segment >= sage.getNumberOfSegments(mf)) { HttpUtil.jsonError(response, 404, "Segment not found: " + segment); return; }
            File media = sage.getFileForSegment(mf, segment);
            if (media == null) { HttpUtil.jsonError(response, 404, "Media segment is unavailable"); return; }
            File edl = findEdl(media);
            List<Marker> markers = edl == null ? new ArrayList<Marker>() : readEdl(edl);
            StringBuilder out = new StringBuilder(1024);
            out.append("{\"id\":").append(id).append(",\"segment\":").append(segment)
               .append(",\"available\":").append(edl != null)
               .append(",\"source\":\"").append(HttpUtil.json(edl == null ? "" : edl.getName())).append("\",")
               .append("\"count\":").append(markers.size()).append(",\"markers\":[");
            for (int i = 0; i < markers.size(); i++) {
                if (i > 0) out.append(','); Marker m = markers.get(i);
                out.append("{\"start\":").append(m.start).append(",\"end\":").append(m.end).append(",\"type\":").append(m.type).append('}');
            }
            out.append("]}"); response.getWriter().write(out.toString());
        } catch (IllegalArgumentException e) { HttpUtil.jsonError(response, 400, e.getMessage()); }
          catch (RuntimeException e) { HttpUtil.jsonError(response, 500, e.getMessage()); }
    }

    private static File findEdl(File media) {
        String p = media.getAbsolutePath(); int dot = p.lastIndexOf('.');
        int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        File a = new File(dot > slash ? p.substring(0, dot) + ".edl" : p + ".edl");
        if (a.isFile() && a.canRead()) return a;
        File b = new File(p + ".edl"); if (b.isFile() && b.canRead()) return b;
        return null;
    }
    private static List<Marker> readEdl(File file) throws IOException {
        List<Marker> out = new ArrayList<Marker>(); BufferedReader r = new BufferedReader(new FileReader(file));
        try { String line; while ((line = r.readLine()) != null) {
            line = line.trim(); if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+"); if (p.length < 2) continue;
            try { double s = Double.parseDouble(p[0]), e = Double.parseDouble(p[1]); int type = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                if (Double.isFinite(s) && Double.isFinite(e) && s >= 0 && e > s && e - s < 3600) { Marker m = new Marker(); m.start=s; m.end=e; m.type=type; out.add(m); }
            } catch (NumberFormatException ignore) {}
        }} finally { r.close(); }
        return out;
    }
}
