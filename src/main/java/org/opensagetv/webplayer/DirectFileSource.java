package org.opensagetv.webplayer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

final class DirectFileSource implements SeekableMediaSource {
    private final File file;
    private final RandomAccessFile input;

    DirectFileSource(File file) throws IOException {
        this.file = file;
        this.input = new RandomAccessFile(file, "r");
    }

    @Override
    public long length() throws IOException {
        return input.length();
    }

    @Override
    public void seek(long position) throws IOException {
        input.seek(position);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return input.read(buffer, offset, length);
    }

    @Override
    public String mode() {
        return "direct-file";
    }

    @Override
    public String detail() {
        return file.getAbsolutePath();
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
