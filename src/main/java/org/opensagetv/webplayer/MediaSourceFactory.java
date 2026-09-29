package org.opensagetv.webplayer;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class MediaSourceFactory {
    static final class Probe {
        final boolean accessible;
        final String mode;
        final String detail;
        final long size;
        final String error;

        Probe(boolean accessible, String mode, String detail, long size, String error) {
            this.accessible = accessible;
            this.mode = mode;
            this.detail = detail;
            this.size = size;
            this.error = error;
        }
    }

    private MediaSourceFactory() {}

    static SeekableMediaSource open(SageApiBridge sage, File file) throws IOException {
        if (file == null) throw new IOException("SageTV returned no physical file for this segment");

        if (file.isFile() && file.canRead()) {
            return new DirectFileSource(file);
        }

        List<String> failures = new ArrayList<String>();
        for (String host : candidateHosts(sage)) {
            try {
                SageMediaServerSource source = new SageMediaServerSource(host, file);
                // Force an immediate SIZE request so access is validated before the servlet sends headers.
                source.length();
                return source;
            } catch (IOException e) {
                failures.add(host + ": " + compact(e.getMessage()));
            }
        }

        StringBuilder message = new StringBuilder();
        message.append("Media segment is not accessible directly and SageTV MediaServer fallback failed. ")
            .append("Path=").append(file.getPath());
        if (!failures.isEmpty()) message.append("; attempts=").append(join(failures, " | "));
        throw new IOException(message.toString());
    }

    static Probe probe(SageApiBridge sage, File file) {
        if (file == null) return new Probe(false, "unavailable", "SageTV returned null file", 0, "No physical file");
        try (SeekableMediaSource source = open(sage, file)) {
            long size = source.length();
            return new Probe(true, source.mode(), source.detail(), size, null);
        } catch (IOException e) {
            return new Probe(false, "unavailable", file.getPath(), 0, compact(e.getMessage()));
        }
    }

    private static Set<String> candidateHosts(SageApiBridge sage) {
        LinkedHashSet<String> hosts = new LinkedHashSet<String>();
        String configured = sage.getServerAddress();
        if (configured != null) {
            configured = configured.trim();
            if (!configured.isEmpty()) hosts.add(stripAddressDecoration(configured));
        }
        // These cover the common case where Jetty is running in the same SageTV JVM.
        hosts.add("127.0.0.1");
        hosts.add("localhost");
        try {
            String local = InetAddress.getLocalHost().getHostName();
            if (local != null && !local.trim().isEmpty()) hosts.add(local.trim());
        } catch (Exception ignored) {
        }
        hosts.remove("");
        return hosts;
    }

    private static String stripAddressDecoration(String value) {
        String host = value;
        if (host.startsWith("/")) host = host.substring(1);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end > 0) return host.substring(1, end);
        }
        // GetServerAddress normally returns only a host. Avoid stripping an IPv6 colon.
        int firstColon = host.indexOf(':');
        int lastColon = host.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) host = host.substring(0, firstColon);
        return host;
    }

    private static String compact(String value) {
        if (value == null || value.trim().isEmpty()) return "unknown error";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(delimiter);
            out.append(values.get(i));
        }
        return out.toString();
    }
}
