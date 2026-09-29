package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AssetStatusServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeStatus(response, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<String> errors = new ArrayList<String>();
        String family = request.getParameter("family");
        if (family != null && !"streaming".equals(family) && !"all".equals(family)) {
            HttpUtil.jsonError(response, 400, "Unknown player asset family");
            return;
        }
        for (String path : "streaming".equals(family) ? AssetCache.streamingList() : AssetCache.prefetchList()) {
            try { AssetCache.get(path, true); }
            catch (IOException e) { errors.add(path + ": " + e.getMessage()); }
        }
        writeStatus(response, errors);
    }

    private void writeStatus(HttpServletResponse response, List<String> errors) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        String root;
        try { root = AssetCache.root().getAbsolutePath(); }
        catch (IOException e) { root = ""; if (errors == null) errors = new ArrayList<String>(); errors.add(e.getMessage()); }
        StringBuilder out = new StringBuilder(2048);
        out.append('{')
           .append("\"libmediaVersion\":\"").append(HttpUtil.json(PluginVersion.LIBMEDIA_VERSION)).append("\",")
           .append("\"hlsjsVersion\":\"").append(HttpUtil.json(PluginVersion.HLSJS_VERSION)).append("\",")
           .append("\"mpegtsjsVersion\":\"").append(HttpUtil.json(PluginVersion.MPEGTSJS_VERSION)).append("\",")
           .append("\"cacheDir\":\"").append(HttpUtil.json(root)).append("\",")
           .append("\"streamingReady\":").append(AssetCache.streamingReady()).append(',')
           .append("\"cachedCount\":").append(AssetCache.cachedCount()).append(',')
           .append("\"expectedCount\":").append(AssetCache.prefetchList().size()).append(',')
           .append("\"cachedBytes\":").append(AssetCache.cachedBytes()).append(',')
           .append("\"ready\":").append(AssetCache.cachedCount() == AssetCache.prefetchList().size());
        if (errors != null) {
            out.append(",\"errors\":[");
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) out.append(',');
                out.append('"').append(HttpUtil.json(errors.get(i))).append('"');
            }
            out.append(']');
        }
        out.append('}');
        response.getWriter().write(out.toString());
    }
}
