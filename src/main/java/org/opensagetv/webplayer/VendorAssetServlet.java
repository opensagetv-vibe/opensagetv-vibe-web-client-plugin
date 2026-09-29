package org.opensagetv.webplayer;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class VendorAssetServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String info = request.getPathInfo();
        String relative = info == null ? "" : info.replaceFirst("^/+", "");
        // New URLs include an explicit asset family (/vendor/libmedia/... or /vendor/hlsjs/...).
        // Keep the historical shorthand (/vendor/decode/...) working by treating it as libmedia.
        if (!relative.startsWith(AssetCache.PREFIX) && !relative.startsWith(AssetCache.HLSJS_PREFIX) && !relative.startsWith(AssetCache.MPEGTS_PREFIX)) {
            relative = AssetCache.PREFIX + relative;
        }
        try {
            File file = AssetCache.get(relative, true);
            response.setContentType(AssetCache.contentType(relative));
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            response.setHeader("X-SageTV-Vendor-Cache", relative.startsWith(AssetCache.MPEGTS_PREFIX) ? "mpegts-"+PluginVersion.MPEGTSJS_VERSION : relative.startsWith(AssetCache.HLSJS_PREFIX) ? "hlsjs-" + PluginVersion.HLSJS_VERSION : "libmedia-" + PluginVersion.LIBMEDIA_VERSION);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLengthLong(file.length());
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file), 128 * 1024)) {
                ServletOutputStream out = response.getOutputStream();
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            response.sendError(502, e.getMessage());
        }
    }
}
