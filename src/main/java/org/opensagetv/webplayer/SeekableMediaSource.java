package org.opensagetv.webplayer;

import java.io.Closeable;
import java.io.IOException;

interface SeekableMediaSource extends Closeable {
    long length() throws IOException;
    void seek(long position) throws IOException;
    int read(byte[] buffer, int offset, int length) throws IOException;
    String mode();
    String detail();
}
