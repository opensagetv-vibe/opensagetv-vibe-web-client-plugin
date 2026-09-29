package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TranscodeStatusServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        TranscoderManager.Probe p = TranscoderManager.probe();
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"available\":" + p.available
                + ",\"executable\":\"" + HttpUtil.json(p.executable) + "\""
                + ",\"detail\":\"" + HttpUtil.json(p.detail) + "\""
                + ",\"source\":\"" + HttpUtil.json(p.source) + "\""
                + ",\"videoEncoder\":\"" + HttpUtil.json(p.videoEncoder) + "\""
                + ",\"hardware\":" + p.hardware
                + ",\"hardwareDecode\":" + p.hardwareDecode
                + ",\"hardwareFilters\":" + p.hardwareFilters
                + ",\"decodeAccelerator\":\"" + HttpUtil.json(p.decodeAccelerator) + "\""
                + ",\"accelerator\":\"" + HttpUtil.json(p.accelerator) + "\"}");
    }
}
