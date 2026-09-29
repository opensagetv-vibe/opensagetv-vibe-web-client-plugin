package org.opensagetv.webplayer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal read-only client for SageTV's built-in MediaServer protocol.
 *
 * This is intentionally implemented here instead of linking against Sage.jar's
 * NetworkRandomFile so the WAR remains compile-time independent of SageTV core
 * classes. It follows the OPENW/SIZE/READ protocol used by SageTV clients.
 */
final class SageMediaServerSource implements SeekableMediaSource {
    static final int DEFAULT_PORT = 7818;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final String host;
    private final int port;
    private final String remotePath;
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private long position;

    SageMediaServerSource(String host, File file) throws IOException {
        this(host, DEFAULT_PORT, file.getPath());
    }

    SageMediaServerSource(String host, int port, String remotePath) throws IOException {
        if (host == null || host.trim().isEmpty()) throw new IOException("Empty SageTV MediaServer host");
        if (remotePath == null || remotePath.isEmpty()) throw new IOException("Empty SageTV media path");
        this.host = host.trim();
        this.port = port;
        this.remotePath = remotePath;
        connectAndOpen();
    }

    private void connectAndOpen() throws IOException {
        closeSocketOnly();
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 128 * 1024));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 16 * 1024));

        output.write("OPENW ".getBytes(StandardCharsets.ISO_8859_1));
        output.write(remotePath.getBytes("UTF-16BE"));
        output.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
        String reply = readLine();
        if (!"OK".equals(reply)) {
            throw new IOException("SageTV MediaServer OPENW failed: " + reply);
        }
        position = 0;
    }

    @Override
    public long length() throws IOException {
        sendAscii("SIZE\r\n");
        String reply = readLine();
        int space = reply.indexOf(' ');
        if (space <= 0) throw new IOException("Unexpected SageTV MediaServer SIZE reply: " + reply);
        try {
            // First value is the currently available size. For a completed recording
            // it is the same as total size; for an active recording it can grow.
            return Long.parseLong(reply.substring(0, space).trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid SageTV MediaServer SIZE reply: " + reply, e);
        }
    }

    @Override
    public void seek(long position) throws IOException {
        if (position < 0) throw new IOException("Negative seek");
        this.position = position;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length <= 0) return 0;
        sendAscii("READ " + position + " " + length + "\r\n");
        try {
            input.readFully(buffer, offset, length);
        } catch (EOFException e) {
            throw e;
        }
        position += length;
        return length;
    }

    @Override
    public String mode() {
        return "sage-mediaserver";
    }

    @Override
    public String detail() {
        return host + ":" + port + " -> " + remotePath;
    }

    @Override
    public void close() throws IOException {
        try {
            if (output != null) {
                try {
                    sendAscii("QUIT\r\n");
                } catch (Exception ignored) {
                }
            }
        } finally {
            closeSocketOnly();
        }
    }

    private void sendAscii(String command) throws IOException {
        if (output == null) throw new IOException("SageTV MediaServer connection is closed");
        output.write(command.getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
    }

    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder(64);
        while (true) {
            int b = input.read();
            if (b < 0) throw new EOFException("Unexpected end of SageTV MediaServer reply");
            if (b == '\n') break;
            if (b != '\r') line.append((char) (b & 0xff));
            if (line.length() > 4096) throw new IOException("SageTV MediaServer reply is too long");
        }
        return line.toString();
    }

    private void closeSocketOnly() {
        closeQuietly(input);
        closeQuietly(output);
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        input = null;
        output = null;
        socket = null;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) {}
        }
    }
}
