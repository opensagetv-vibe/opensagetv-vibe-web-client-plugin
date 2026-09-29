package org.opensagetv.webplayer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies production discovery uses the FFmpeg plugin bridge, not stock ffmpeg. */
public final class TranscoderPluginResolutionSmoke {
    public static void main(String[] args) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        String oldTranscoder = System.getProperty("sagetv.webplayer.transcoder");
        String oldFfmpeg = System.getProperty("sagetv.webplayer.ffmpeg");
        Path root = Files.createTempDirectory("vibe-web-transcoder-");
        try {
            Path launcher = root.resolve("SageTVTranscoder");
            Files.write(launcher, "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
            launcher.toFile().setExecutable(true, false);
            System.clearProperty("sagetv.webplayer.transcoder");
            System.clearProperty("sagetv.webplayer.ffmpeg");
            System.setProperty("user.dir", root.toString());
            same(launcher.toFile(), TranscoderManager.findExecutable(), "server-root plugin bridge");

            Path explicit = root.resolve("explicit-transcoder");
            Files.write(explicit, "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
            explicit.toFile().setExecutable(true, false);
            System.setProperty("sagetv.webplayer.transcoder", explicit.toString());
            same(explicit.toFile(), TranscoderManager.findExecutable(), "explicit plugin transcoder override");

            System.clearProperty("sagetv.webplayer.transcoder");
            System.setProperty("sagetv.webplayer.ffmpeg", explicit.toString());
            same(explicit.toFile(), TranscoderManager.findExecutable(), "legacy isolated-test override");
            System.out.println("PASS: FFmpeg plugin transcoder resolution");
        } finally {
            restore("user.dir", oldUserDir);
            restore("sagetv.webplayer.transcoder", oldTranscoder);
            restore("sagetv.webplayer.ffmpeg", oldFfmpeg);
            delete(root.toFile());
        }
    }

    private static void same(File expected, File actual, String label) throws Exception {
        if (actual == null || !expected.getCanonicalFile().equals(actual.getCanonicalFile())) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name); else System.setProperty(name, value);
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
