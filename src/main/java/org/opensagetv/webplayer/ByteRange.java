package org.opensagetv.webplayer;

final class ByteRange {
    final long start;
    final long end;

    ByteRange(long start, long end) {
        this.start = start;
        this.end = end;
    }

    long length() { return end - start + 1; }

    static ByteRange parseSingle(String header, long fileLength) {
        if (header == null || header.trim().isEmpty()) return null;
        if (!header.startsWith("bytes=")) throw new IllegalArgumentException("Only byte ranges are supported");
        String spec = header.substring(6).trim();
        if (spec.contains(",")) throw new IllegalArgumentException("Multiple ranges are not supported");
        int dash = spec.indexOf('-');
        if (dash < 0) throw new IllegalArgumentException("Malformed Range header");

        String left = spec.substring(0, dash).trim();
        String right = spec.substring(dash + 1).trim();
        long start;
        long end;
        try {
            if (left.isEmpty()) {
                long suffix = Long.parseLong(right);
                if (suffix <= 0) throw new IllegalArgumentException("Invalid suffix range");
                suffix = Math.min(suffix, fileLength);
                start = fileLength - suffix;
                end = fileLength - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? fileLength - 1 : Long.parseLong(right);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed Range header");
        }
        if (fileLength <= 0 || start < 0 || start >= fileLength || end < start) {
            throw new IllegalArgumentException("Range is outside the current file");
        }
        end = Math.min(end, fileLength - 1);
        return new ByteRange(start, end);
    }
}
