package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HealthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            SageApiBridge.create();
            TranscoderManager.Probe tx = TranscoderManager.probe();
            response.getWriter().write("{\"ok\":true,\"sagex\":true,\"version\":\"" + PluginVersion.VERSION
                + "\",\"libmediaVersion\":\"" + PluginVersion.LIBMEDIA_VERSION
                + "\",\"decoderAssetsCached\":" + AssetCache.cachedCount()
                + ",\"decoderAssetsExpected\":" + AssetCache.prefetchList().size()
                + ",\"transcoderAvailable\":" + tx.available + "}");
        } catch (RuntimeException e) {
            response.setStatus(503);
            response.getWriter().write("{\"ok\":false,\"sagex\":false,\"version\":\"" + PluginVersion.VERSION
                + "\",\"error\":\"" + HttpUtil.json(e.getMessage()) + "\"}");
        }
    }
}
