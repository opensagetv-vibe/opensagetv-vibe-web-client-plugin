package org.opensagetv.webplayer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Strict, pinned, allow-list cache for third-party browser assets.
 *
 * The browser only talks to the SageTV Jetty app. On a cache miss the server may
 * retrieve an approved libmedia 1.3.1 file, store it locally and serve it on the
 * next request. There is intentionally no arbitrary URL proxy.
 */
final class AssetCache {
    static final String PREFIX = "libmedia/";
    static final String HLSJS_PREFIX = "hlsjs/";
    static final String MPEGTS_PREFIX = "mpegts-" + PluginVersion.MPEGTSJS_VERSION + "/";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int BUFFER = 128 * 1024;
    private static final String V = PluginVersion.LIBMEDIA_VERSION;

    private static final List<String> AVPLAYER_CHUNKS = Collections.unmodifiableList(Arrays.asList(
        "26.avplayer.js", "33.avplayer.js", "43.avplayer.js", "97.avplayer.js", "163.avplayer.js",
        "182.avplayer.js", "195.avplayer.js", "199.avplayer.js", "239.avplayer.js", "248.avplayer.js",
        "261.avplayer.js", "272.avplayer.js", "325.avplayer.js", "384.avplayer.js", "462.avplayer.js",
        "502.avplayer.js", "573.avplayer.js", "630.avplayer.js", "641.avplayer.js", "735.avplayer.js",
        "747.avplayer.js", "749.avplayer.js", "790.avplayer.js", "794.avplayer.js", "850.avplayer.js",
        "881.avplayer.js", "932.avplayer.js", "943.avplayer.js", "956.avplayer.js", "961.avplayer.js",
        "991.avplayer.js"
    ));

    private static final List<String> CODECS = Collections.unmodifiableList(Arrays.asList(
        "mpeg2video", "ac3", "eac3", "h264", "aac", "mp3", "hevc", "av1", "vp8", "vp9",
        "opus", "flac", "vorbis", "dca", "mpeg4", "vvc", "mjpeg"
    ));

    private AssetCache() {}

    static File root() throws IOException {
        String explicit = firstNonEmpty(System.getProperty("sagetv.webplayer.cacheDir"), System.getenv("SAGETV_WEBPLAYER_CACHE_DIR"));
        List<File> candidates = new ArrayList<File>();
        if (explicit != null) candidates.add(new File(explicit));
        String userDir = System.getProperty("user.dir");
        if (userDir != null) candidates.add(new File(userDir, "webplayer-cache"));
        String home = System.getProperty("user.home");
        if (home != null) candidates.add(new File(home, ".sagetv-webplayer-cache"));
        candidates.add(new File(System.getProperty("java.io.tmpdir"), "sagetv-webplayer-cache"));

        IOException last = null;
        for (File candidate : candidates) {
            try {
                File versioned = new File(candidate, "libmedia-" + V);
                if (!versioned.exists() && !versioned.mkdirs()) throw new IOException("Could not create " + versioned);
                File probe = new File(versioned, ".write-test");
                FileOutputStream out = new FileOutputStream(probe);
                out.write(1);
                out.close();
                if (!probe.delete()) probe.deleteOnExit();
                return versioned;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("No writable browser asset cache directory") : last;
    }

    static File get(String relative, boolean fetchIfMissing) throws IOException {
        validate(relative);
        File file = new File(root(), relative.replace('/', File.separatorChar));
        File canonicalRoot = root().getCanonicalFile();
        File canonical = file.getCanonicalFile();
        if (!canonical.getPath().startsWith(canonicalRoot.getPath() + File.separator)) {
            throw new IOException("Invalid asset path");
        }
        if (canonical.isFile() && canonical.length() > 0) return canonical;
        if (!fetchIfMissing) return canonical;
        fetch(relative, canonical);
        return canonical;
    }

    static synchronized void fetch(String relative, File destination) throws IOException {
        if (destination.isFile() && destination.length() > 0) return;
        validate(relative);
        URL url = new URL(remoteUrl(relative));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "SageTV-WebPlayer/" + PluginVersion.VERSION);
        conn.setRequestProperty("Accept", "*/*");
        int status = conn.getResponseCode();
        if (status < 200 || status > 299) {
            conn.disconnect();
            throw new IOException("Asset download HTTP " + status + " for " + relative);
        }
        File parent = destination.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            conn.disconnect();
            throw new IOException("Could not create asset cache folder " + parent);
        }
        File temp = new File(destination.getPath() + ".part");
        try (InputStream in = new BufferedInputStream(conn.getInputStream(), BUFFER);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(temp), BUFFER)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) continue;
                out.write(buffer, 0, read);
                total += read;
                if (total > 128L * 1024L * 1024L) throw new IOException("Asset exceeded 128 MiB limit: " + relative);
            }
        } finally {
            conn.disconnect();
        }
        if (!temp.isFile() || temp.length() == 0) {
            temp.delete();
            throw new IOException("Downloaded empty asset: " + relative);
        }
        if (destination.exists() && !destination.delete()) {
            temp.delete();
            throw new IOException("Could not replace cached asset " + destination);
        }
        if (!temp.renameTo(destination)) {
            copy(temp, destination);
            if (!temp.delete()) temp.deleteOnExit();
        }
    }

    static List<String> streamingList() {
        return Arrays.asList(HLSJS_PREFIX + "hls.min.js", MPEGTS_PREFIX + "mpegts.min.js");
    }

    static boolean streamingReady() {
        try {
            for (String path : streamingList()) {
                File f = get(path, false);
                if (!f.isFile() || f.length() == 0) return false;
            }
            return true;
        } catch (IOException e) { return false; }
    }

    static List<String> prefetchList() {
        List<String> out = new ArrayList<String>();
        out.add(PREFIX + "cheap-polyfill.js");
        out.add(PREFIX + "umd/avplayer.js");
        for (String chunk : AVPLAYER_CHUNKS) out.add(PREFIX + "umd/" + chunk);
        for (String codec : CODECS) {
            out.add(PREFIX + "decode/" + codec + ".wasm");
            out.add(PREFIX + "decode/" + codec + "-atomic.wasm");
            out.add(PREFIX + "decode/" + codec + "-simd.wasm");
        }
        out.add(PREFIX + "resample/resample.wasm");
        out.add(PREFIX + "resample/resample-atomic.wasm");
        out.add(PREFIX + "resample/resample-simd.wasm");
        out.add(PREFIX + "stretchpitch/stretchpitch.wasm");
        out.add(PREFIX + "stretchpitch/stretchpitch-atomic.wasm");
        out.add(PREFIX + "stretchpitch/stretchpitch-simd.wasm");
        out.add(HLSJS_PREFIX + "hls.min.js");
        out.add(MPEGTS_PREFIX + "mpegts.min.js");
        return Collections.unmodifiableList(out);
    }

    static long cachedBytes() {
        try { return sum(root()); } catch (IOException e) { return 0; }
    }

    static int cachedCount() {
        int count = 0;
        for (String path : prefetchList()) {
            try { if (get(path, false).isFile()) count++; } catch (IOException ignored) {}
        }
        return count;
    }

    static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                byte[] b = new byte[64 * 1024];
                int n;
                while ((n = in.read(b)) >= 0) if (n > 0) digest.update(b, 0, n);
            }
            StringBuilder s = new StringBuilder();
            for (byte x : digest.digest()) s.append(String.format("%02x", x & 0xff));
            return s.toString();
        } catch (Exception e) {
            throw new IOException("Could not calculate SHA-256", e);
        }
    }

    static String contentType(String relative) {
        String lower = relative.toLowerCase();
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        return "application/octet-stream";
    }

    private static String remoteUrl(String relative) throws IOException {
        validate(relative);
        if(relative.startsWith(MPEGTS_PREFIX))return "https://cdn.jsdelivr.net/npm/mpegts.js@"+PluginVersion.MPEGTSJS_VERSION+"/dist/mpegts.min.js";
        if (relative.startsWith(HLSJS_PREFIX)) {
            return "https://cdn.jsdelivr.net/npm/hls.js@" + PluginVersion.HLSJS_VERSION + "/dist/hls.min.js";
        }
        String p = relative.substring(PREFIX.length());
        if (p.equals("cheap-polyfill.js")) {
            return "https://cdn.jsdelivr.net/gh/zhaohappy/libmedia@v" + V + "/dist/cheap-polyfill.js";
        }
        if (p.startsWith("umd/")) {
            return "https://cdn.jsdelivr.net/npm/@libmedia/avplayer@" + V + "/dist/umd/" + p.substring(4);
        }
        if (p.startsWith("decode/") || p.startsWith("resample/") || p.startsWith("stretchpitch/")) {
            return "https://cdn.jsdelivr.net/gh/zhaohappy/libmedia@v" + V + "/dist/" + p;
        }
        throw new IOException("Asset path not allowed: " + relative);
    }

    private static void validate(String relative) throws IOException {
        if (relative == null || relative.contains("..") || relative.contains("\\") || relative.contains(":")) {
            throw new IOException("Asset path not allowed");
        }
        if(relative.startsWith(MPEGTS_PREFIX)){
            if((MPEGTS_PREFIX+"mpegts.min.js").equals(relative))return;
            throw new IOException("Unknown mpegts.js asset");
        }
        if (relative.startsWith(HLSJS_PREFIX)) {
            if ((HLSJS_PREFIX + "hls.min.js").equals(relative)) return;
            throw new IOException("Unknown hls.js asset: " + relative);
        }
        if (!relative.startsWith(PREFIX)) throw new IOException("Asset path not allowed");
        String p = relative.substring(PREFIX.length());
        if (p.equals("cheap-polyfill.js")) return;
        if (p.startsWith("umd/")) {
            String name = p.substring(4);
            if (name.equals("avplayer.js") || name.matches("[0-9]+\\.avplayer\\.js")) return;
            throw new IOException("Unknown AVPlayer asset: " + name);
        }
        if (p.startsWith("decode/") && p.endsWith(".wasm")) {
            String name = p.substring("decode/".length(), p.length() - ".wasm".length());
            if (name.endsWith("-simd")) name = name.substring(0, name.length() - 5);
            if (name.endsWith("-atomic")) name = name.substring(0, name.length() - 7);
            if (CODECS.contains(name)) return;
        }
        if (p.matches("resample/resample(-(simd|atomic))?\\.wasm")) return;
        if (p.matches("stretchpitch/stretchpitch(-(simd|atomic))?\\.wasm")) return;
        throw new IOException("Asset path not allowed: " + relative);
    }

    private static long sum(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += sum(child);
        return total;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }

    private static void copy(File source, File dest) throws IOException {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[BUFFER];
            int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
        }
    }
}
