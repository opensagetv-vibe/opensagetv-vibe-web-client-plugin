package org.opensagetv.webplayer;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional compatibility fallback: external FFmpeg/MIM-compatible executable ->
 * continuously streamed fragmented H.264/AAC MP4. Hardware H.264 encoding is
 * preferred automatically. The original/Wasm path never requires it.
 */
public class TranscodeServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Process process = null;
        File concatList = null;
        try {
            int id = HttpUtil.requiredInt(request.getParameter("id"), "id");
            int segment = HttpUtil.optionalInt(request.getParameter("segment"), 0, "segment");
            int audio = HttpUtil.optionalInt(request.getParameter("audio"), 0, "audio");
            double start = optionalDouble(request.getParameter("start"), 0);
            if (segment < 0 || audio < 0 || start < 0) throw new IllegalArgumentException("segment/audio/start must be >= 0");

            TranscoderManager.Probe selected = TranscoderManager.probe();
            if (!selected.available) {
                response.sendError(503, selected.detail);
                return;
            }

            SageApiBridge sage = SageApiBridge.create();
            Object mf = sage.getMediaFile(id);
            if (mf == null) { response.sendError(404, "MediaFile not found"); return; }
            int count = sage.getNumberOfSegments(mf);
            if (segment >= count) { response.sendError(404, "Segment not found"); return; }

            List<File> files = new ArrayList<File>();
            for (int i = segment; i < count; i++) {
                File f = sage.getFileForSegment(mf, i);
                if (f == null || !f.isFile() || !f.canRead()) {
                    response.sendError(409, "External transcoder requires directly readable recording segments; original SageTV MediaServer streaming may still work.");
                    return;
                }
                files.add(f);
            }
            if (files.isEmpty()) { response.sendError(404, "No readable recording segment found"); return; }

            boolean concat = files.size() > 1;
            File input = files.get(0);
            if (concat) {
                concatList = createConcatList(files);
                input = concatList;
            }

            StartResult started = start(selected, input, concat, start, audio);
            process = started.process;
            byte[] first = started.first;
            int firstLen = started.firstLen;
            TranscoderManager.Probe actual = selected;

            // A hardware path always tries decode + encode first. If the decode/VPP
            // side cannot initialize, retain the GPU encoder and retry with software
            // decode/filtering before falling all the way back to libx264.
            if (firstLen < 0 && selected.hardware && selected.hardwareDecode) {
                if (process != null) process.destroy();
                actual = TranscoderManager.encodeOnlyFallback(selected, "hardware decode/VPP initialization failed");
                started = start(actual, input, concat, start, audio);
                process = started.process; first = started.first; firstLen = started.firstLen;
            }
            if (firstLen < 0 && actual.hardware) {
                if (process != null) process.destroy();
                actual = TranscoderManager.softwareFallback(actual, "hardware encode initialization failed");
                started = start(actual, input, concat, start, audio);
                process = started.process; first = started.first; firstLen = started.firstLen;
            }
            if (firstLen < 0) {
                response.sendError(502, "FFmpeg compatibility transcoder exited before producing video output.");
                return;
            }

            response.setBufferSize(64 * 1024);
            response.setContentType("video/mp4");
            response.setHeader("Cache-Control", "no-store, no-transform");
            response.setHeader("X-Accel-Buffering", "no");
            response.setHeader("X-SageTV-Playback-Mode", "TRANSCODE_FMP4");
            response.setHeader("X-SageTV-Transcoder", "external-ffmpeg");
            response.setHeader("X-SageTV-Video-Encoder", actual.videoEncoder);
            response.setHeader("X-SageTV-Hardware-Transcode", String.valueOf(actual.hardware));
            response.setHeader("X-SageTV-Hardware-Decode", String.valueOf(actual.hardwareDecode));
            response.setHeader("X-SageTV-Hardware-Filters", String.valueOf(actual.hardwareFilters));
            response.setHeader("X-SageTV-Decode-Accelerator", actual.decodeAccelerator);
            response.setHeader("X-SageTV-Segments", String.valueOf(files.size()));
            ServletOutputStream out = response.getOutputStream();
            out.write(first, 0, firstLen);
            out.flush();
            try (InputStream in = started.input) {
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    if (n == 0) continue;
                    out.write(buffer, 0, n);
                    out.flush();
                }
            } catch (IOException clientClosed) {
                // Seeking/stopping aborts the browser fetch and commonly closes this response.
                if (process != null && process.isAlive()) process.destroy();
                return;
            }
            try { process.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        } catch (IllegalArgumentException e) {
            if (!response.isCommitted()) response.sendError(400, e.getMessage());
        } finally {
            if (process != null && process.isAlive()) process.destroy();
            if (concatList != null && concatList.exists()) concatList.delete();
        }
    }

    private static final class StartResult {
        final Process process;
        final BufferedInputStream input;
        final byte[] first;
        final int firstLen;
        StartResult(Process process, BufferedInputStream input, byte[] first, int firstLen) {
            this.process = process; this.input = input; this.first = first; this.firstLen = firstLen;
        }
    }

    private static StartResult start(TranscoderManager.Probe probe, File input, boolean concat, double start, int audio) throws IOException {
        List<String> command = TranscoderManager.buildFmp4Command(probe, input, concat, start, audio);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = pb.start();
        BufferedInputStream in = new BufferedInputStream(process.getInputStream(), 256 * 1024);
        byte[] first = new byte[32 * 1024];
        int n = in.read(first);
        return new StartResult(process, in, first, n);
    }

    private static File createConcatList(List<File> files) throws IOException {
        File list = File.createTempFile("sagetv-webplayer-", ".ffconcat");
        BufferedWriter w = new BufferedWriter(new FileWriter(list));
        try {
            w.write("ffconcat version 1.0\n");
            for (File file : files) {
                String path = file.getAbsolutePath().replace("\\", "\\\\").replace("'", "'\\''");
                w.write("file '"); w.write(path); w.write("'\n");
            }
        } finally {
            w.close();
        }
        return list;
    }

    private static double optionalDouble(String value, double fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number: " + value); }
    }
}
