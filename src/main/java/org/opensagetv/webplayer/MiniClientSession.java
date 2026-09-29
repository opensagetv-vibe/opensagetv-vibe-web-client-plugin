package org.opensagetv.webplayer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real SageTV MiniClient protocol bridge for a browser session.
 *
 * Wire ordering follows opensagetv-vibe-android-client source/dev core:
 * media connection type 1 first, graphics connection type 0 second; four-byte
 * command framing; shared graphics event/reply channel; Sage command event 136.
 */
final class MiniClientSession {
    private static final int DEFAULT_PORT = 31099;
    private static final int DRAWING_CMD_TYPE = 16;
    private static final int GET_PROPERTY_CMD_TYPE = 0;
    private static final int SET_PROPERTY_CMD_TYPE = 1;
    private static final int FS_CMD_TYPE = 2;
    private static final int SAGECOMMAND_EVENT_REPLY_TYPE = 136;
    private static final int KB_EVENT_REPLY_TYPE = 129;
    private static final int MPRESS_EVENT_REPLY_TYPE = 130;
    private static final int MRELEASE_EVENT_REPLY_TYPE = 131;
    private static final int MCLICK_EVENT_REPLY_TYPE = 132;
    private static final int MMOVE_EVENT_REPLY_TYPE = 133;
    private static final int MDRAG_EVENT_REPLY_TYPE = 134;
    private static final int MWHEEL_EVENT_REPLY_TYPE = 135;
    private static final int UI_RESIZE_EVENT_REPLY_TYPE = 192;
    private static final int UI_REPAINT_EVENT_REPLY_TYPE = 193;
    private static final int SUBTITLE_UPDATE_REPLY_TYPE = 225;

    // GFX command IDs from Vibe GFXCMD2.
    static final int GFX_INIT = 1;
    static final int GFX_DEINIT = 2;
    static final int GFX_LOADIMAGE = 26;
    static final int GFX_UNLOADIMAGE = 27;
    static final int GFX_FLIPBUFFER = 30;
    static final int GFX_STARTFRAME = 31;
    static final int GFX_LOADIMAGELINE = 32;
    static final int GFX_PREPIMAGE = 33;
    static final int GFX_LOADIMAGECOMPRESSED = 34;
    static final int GFX_XFMIMAGE = 35;
    static final int GFX_CREATESURFACE = 37;
    static final int GFX_SETTARGETSURFACE = 38;
    static final int GFX_LOADCACHEDIMAGE = 44;
    static final int GFX_LOADIMAGETARGETED = 45;
    static final int GFX_PREPIMAGETARGETED = 46;
    static final int GFX_SETVIDEOPROP = 130;
    static final int GFX_MEDIA_RECONNECT = 131;

    private final String id;
    private final String server;
    private final int port;
    private final String clientId;
    private volatile int width;
    private volatile int height;
    private volatile boolean alive;
    private volatile Socket mediaSocket;
    private volatile Socket gfxSocket;
    private volatile DataInputStream gfxIn;
    private volatile DataOutputStream eventOut;
    private final Object lifecycleLock = new Object();
    private final Object eventLock = new Object();
    private final MiniClientCrypto crypto = new MiniClientCrypto();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<Socket> openingSockets = new ArrayList<Socket>();
    private final CountDownLatch mediaReady = new CountDownLatch(1);
    private volatile boolean reconnectAllowed, firstFrameStarted, reconnecting;
    // All fields below are protected by eventLock. INIT acknowledges a driver,
    // not a fully constructed SageTV UI. Unsolicited events must wait until
    // the first FLIPBUFFER reply, when the server has actually rendered a UI.
    private boolean uiEventsReady, firstFrameCompleted;
    private boolean pendingResize = true, pendingRepaint, pendingMediaUpdate;
    private int pendingWidth, pendingHeight;
    private int deferredUiEvents, droppedEarlyInput, sentUiEvents;
    private int lastUiEventType = -1;
    private volatile long closedAt;
    private volatile String phase = "created", lastGfxCommand = "none", lastMediaCommand = "none";
    private volatile String lastTransportError = "";
    private final AtomicInteger gfxFrames = new AtomicInteger(), reconnectAttempts = new AtomicInteger();
    private final AtomicInteger reconnectSuccesses = new AtomicInteger();
    private final List<String> trace = new ArrayList<String>();
    private Thread mediaThread;
    private Thread gfxThread;
    private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<String>(20000);
    private final AtomicInteger replyCount = new AtomicInteger();
    private final AtomicInteger gfxHandle = new AtomicInteger(2);
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile int eventQueuePeak;
    private final MiniClientMediaBridge mediaBridge;
    private volatile String lastError = "";
    private volatile int browserVideoWidth = 1920;
    private volatile int browserVideoHeight = 1080;
    private volatile boolean subtitleCallbacks;
    private final boolean unifiedGraphics;
    private final GfxOpcodeLedger gfxOpcodeLedger = new GfxOpcodeLedger();
    private volatile long subtitleCallbackEvents, subtitleCallbackBytes;

    MiniClientSession(String id, String server, String clientId, int width, int height) {
        this(id, server, clientId, width, height, false);
    }

    MiniClientSession(String id, String server, String clientId, int width, int height, boolean unifiedGraphics) {
        this.id = id;
        String host = server == null || server.trim().isEmpty() ? "127.0.0.1" : server.trim();
        int p = DEFAULT_PORT;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(']') < 0) {
            try { p = Integer.parseInt(host.substring(colon + 1)); host = host.substring(0, colon); } catch (Exception ignored) {}
        }
        this.server = host;
        this.port = p;
        this.clientId = normalizeClientId(clientId);
        this.width = Math.max(320, Math.min(1920, width));
        this.height = Math.max(240, Math.min(1080, height));
        this.pendingWidth = this.width;
        this.pendingHeight = this.height;
        this.unifiedGraphics = unifiedGraphics;
        this.mediaBridge = new MiniClientMediaBridge(this);
    }

    String id() { return id; }
    String server() { return server; }
    int width() { return width; }
    int height() { return height; }
    boolean isAlive() { return alive; }
    String lastError() { return lastError; }
    MiniClientMediaBridge media() { return mediaBridge; }

    String identityKey() { return server.toLowerCase(Locale.ROOT) + ":" + port + "/" + clientId; }
    long closedAt() { return closedAt; }

    void start() throws IOException {
        phase = "connecting";
        note("start: media type 1, then GFX type 0");
        enqueue(statusJson("connecting"));
        try {
            if (!installMedia(establish(1, 30000))) throw new IOException("Connection was closed during startup");
            if (!installGfx(establish(0, 30000))) throw new IOException("Connection was closed during startup");
            synchronized (lifecycleLock) {
                if (closed.get()) throw new IOException("Connection was closed during startup");
                alive = true;
            }
            mediaThread = new Thread(new Runnable() { @Override public void run() { mediaLoop(); } }, "web-vibe-media-" + shortId());
            mediaThread.setDaemon(true);
            mediaThread.start();
            try {
                if (!mediaReady.await(2, TimeUnit.SECONDS)) throw new IOException("Media worker readiness timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new IOException("Startup interrupted", e);
            }
            if (!alive) throw new IOException("Connection closed before GFX worker startup");
            phase = "negotiating";
            note("media worker ready; starting GFX worker");
            // Publish before launching the reader so a fast failure cannot be
            // followed by an incorrect late 'connected' event.
            enqueue(statusJson("connected"));
            gfxThread = new Thread(new Runnable() { @Override public void run() { gfxLoop(); } }, "web-vibe-gfx-" + shortId());
            gfxThread.setDaemon(true);
            gfxThread.start();
        } catch (IOException e) {
            fail("MiniClient startup failed: " + exceptionText(e));
            throw e;
        }
    }

    private Socket establish(int connectionType, int timeoutMs) throws IOException {
        Socket s = new Socket();
        boolean accepted = false;
        synchronized (lifecycleLock) {
            if (closed.get()) throw new IOException("MiniClient is closed");
            openingSockets.add(s);
        }
        note("handshake type " + connectionType + " started");
        try {
            s.connect(new InetSocketAddress(server, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            OutputStream out = s.getOutputStream();
            byte[] msg = new byte[8]; msg[0] = 1;
            System.arraycopy(clientIdBytes(clientId), 0, msg, 1, 6);
            msg[7] = (byte) connectionType;
            out.write(msg); out.flush();
            int reply = s.getInputStream().read();
            if (reply != 2) throw new IOException("SageTV MiniClient handshake type " + connectionType + " rejected with reply " + reply);
            if (closed.get()) throw new IOException("Connection closed during handshake");
            s.setSoTimeout(0); accepted = true;
            note("handshake type " + connectionType + " accepted");
            return s;
        } finally {
            synchronized (lifecycleLock) { openingSockets.remove(s); }
            if (!accepted) closeSocket(s);
        }
    }

    private boolean installMedia(Socket s) {
        synchronized (lifecycleLock) {
            if (closed.get()) { closeSocket(s); return false; }
            mediaSocket = s; return true;
        }
    }

    private boolean installGfx(Socket s) throws IOException {
        try {
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            synchronized (lifecycleLock) {
                if (closed.get()) { closeSocket(s); return false; }
                synchronized (eventLock) { gfxSocket = s; gfxIn = in; eventOut = out; }
            }
            return true;
        } catch (IOException e) { closeSocket(s); throw e; }
    }

    private void gfxLoop() {
        try {
            while (alive) {
                try {
                    DataInputStream input = gfxIn;
                    byte[] header = new byte[4];
                    input.readFully(header);
                    int type = header[0] & 0xff;
                    int len = ((header[1] & 0xff) << 16) | ((header[2] & 0xff) << 8) | (header[3] & 0xff);
                    byte[] body = new byte[len];
                    input.readFully(body);
                    // Never dispatch a partially read frame, including after a
                    // successful type-5 reconnect. Start at a new header instead.
                    gfxFrames.incrementAndGet();
                    if (type == DRAWING_CMD_TYPE) handleGfx(body, len);
                    else if (type == GET_PROPERTY_CMD_TYPE) handleGetProperty(body, len);
                    else if (type == SET_PROPERTY_CMD_TYPE) handleSetProperty(body, len);
                    else if (type == FS_CMD_TYPE) { lastGfxCommand = "filesystem"; handleFs(body, len); }
                    else note("ignored GFX envelope type " + type + " bytes " + len);
                } catch (IOException e) {
                    if (!alive) break;
                    lastTransportError = exceptionText(e);
                    note("GFX I/O failed after " + lastGfxCommand + ": " + lastTransportError);
                    if (canRecoverGfx() && recoverGfx()) continue;
                    if (alive) fail("GFX connection ended: " + lastTransportError);
                    break;
                }
            }
        } catch (Exception e) {
            if (alive) fail("GFX protocol failure after " + lastGfxCommand + ": " + exceptionText(e));
        } finally { close(); }
    }

    private boolean canRecoverGfx() {
        synchronized (eventLock) {
            // Same rule as Vibe: type-5 cannot restore an encrypted event link.
            boolean eligible = reconnectAllowed && firstFrameStarted && !crypto.enabled();
            if (crypto.enabled()) note("native type-5 recovery suppressed while event encryption is active");
            return eligible;
        }
    }

    private boolean recoverGfx() {
        reconnecting = true; phase = "gfx-reconnecting";
        enqueue(statusJson("gfx-reconnecting"));
        // Close first to unblock any writer holding eventLock.
        closeSocket(gfxSocket);
        synchronized (eventLock) { eventOut = null; gfxIn = null; }
        final long[] delays = {0L, 150L, 350L};
        try {
            for (long delay : delays) {
                if (!alive || closed.get()) return false;
                try {
                    if (delay > 0L) Thread.sleep(delay);
                    if (!alive || closed.get()) return false;
                    reconnectAttempts.incrementAndGet();
                    if (!installGfx(establish(5, 5000))) return false;
                    reconnectSuccesses.incrementAndGet(); phase = "active";
                    note("GFX type-5 recovery succeeded; media and image handles retained");
                    reconnecting = false;
                    enqueue(statusJson("gfx-reconnected"));
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return false;
                } catch (IOException e) { note("GFX recovery attempt failed: " + exceptionText(e)); }
            }
            note("GFX recovery exhausted three attempts");
            return false;
        } finally { reconnecting = false; }
    }

    private void handleGfx(byte[] body, int len) throws IOException {
        if (len < 4) throw new IllegalArgumentException("GFX command shorter than four-byte header");
        int cmd = body[0] & 0xff;
        GfxOpcodeLedger.Disposition gfxDisposition = gfxOpcodeLedger.encounter(cmd);
        if (gfxDisposition != GfxOpcodeLedger.Disposition.IMPLEMENTED) {
            note("GFX " + GfxOpcodeLedger.name(cmd) + " received with disposition " + gfxDisposition);
            enqueue("{\"type\":\"gfxUnsupported\",\"cmd\":" + cmd + ",\"name\":\"" + HttpUtil.json(GfxOpcodeLedger.name(cmd)) + "\",\"disposition\":\"" + gfxDisposition + "\"}");
        }
        lastGfxCommand = "draw " + cmd + " (" + len + " bytes)";
        if (cmd == GFX_STARTFRAME) {
            if (!firstFrameStarted) note("first GFX STARTFRAME");
            firstFrameStarted = true; phase = "active";
        }
        if (cmd == GFX_DEINIT) { note("server GFX DEINIT"); close(); return; }
        if (cmd == GFX_MEDIA_RECONNECT) {
            try { if (mediaSocket != null) mediaSocket.close(); } catch (Exception ignored) {}
            return;
        }
        int returnValue = 0;
        boolean hasReturn = false;
        int assignedHandle = 0;
        switch (cmd) {
            case GFX_INIT:
                synchronized (eventLock) {
                    uiEventsReady = false;
                    firstFrameCompleted = false;
                    firstFrameStarted = false;
                    phase = "negotiating";
                    // Preserve a newer browser size already waiting on startup.
                    if (!pendingResize) { pendingWidth = width; pendingHeight = height; }
                    pendingResize = true;
                    note("GFX INIT acknowledged; UI events deferred until first FLIPBUFFER");
                }
                hasReturn = true; returnValue = 1; break;
            case GFX_FLIPBUFFER:
                hasReturn = true; returnValue = 0; break;
            case GFX_LOADIMAGE:
                int imageFormat = gfxArgInt(body, len, 8, 0);
                hasReturn = true;
                if (imageFormat == 256 && !unifiedGraphics) {
                    assignedHandle = 0; returnValue = 0;
                    note("Rejected format-256 image while unified graphics are disabled");
                } else {
                    assignedHandle = gfxHandle.getAndIncrement(); returnValue = assignedHandle;
                }
                break;
            case GFX_CREATESURFACE:
                assignedHandle = gfxHandle.getAndIncrement();
                hasReturn = true; returnValue = assignedHandle; break;
            case GFX_PREPIMAGE:
                hasReturn = true; returnValue = 1; break;
            case GFX_LOADIMAGECOMPRESSED:
            case GFX_XFMIMAGE:
                assignedHandle = gfxHandle.getAndIncrement();
                hasReturn = true; returnValue = assignedHandle; break;
            default:
                break;
        }
        String payload = Base64.getEncoder().encodeToString(body);
        enqueue("{\"type\":\"gfx\",\"seq\":" + sequence.incrementAndGet() +
                ",\"cmd\":" + cmd + ",\"assignedHandle\":" + assignedHandle +
                ",\"payload\":\"" + payload + "\"}");
        if (hasReturn) writeReply(DRAWING_CMD_TYPE, intBytes(returnValue));
        if (cmd == GFX_FLIPBUFFER) enableUiEventsAfterFirstFrame();
    }

    /**
     * A reply must precede asynchronous notifications on the shared wire. In
     * particular, event 193 dereferences SageTV's root panel; after INIT that
     * panel may still be null. No sleep, server authentication change, or
     * dependency on a browser-specific ready callback is needed here.
     */
    private void enableUiEventsAfterFirstFrame() throws IOException {
        synchronized (eventLock) {
            if (uiEventsReady || !alive || closed.get()) return;
            firstFrameCompleted = true;
            uiEventsReady = true;
            phase = "active";
            note("first GFX FLIPBUFFER acknowledged; releasing deferred UI events");
            if (pendingResize) {
                sendResizeNow(pendingWidth, pendingHeight);
                pendingResize = false;
                pendingRepaint = false; // Resize already requests a full repaint.
            } else if (pendingRepaint) {
                sendRepaintNow(0, 0, width, height);
                pendingRepaint = false;
            }
            if (pendingMediaUpdate) {
                writeReply(201, new byte[0]);
                pendingMediaUpdate = false;
            }
        }
    }

    private void handleGetProperty(byte[] body, int len) throws IOException {
        String name = new String(body, 0, len, StandardCharsets.ISO_8859_1).trim();
        lastGfxCommand = "get property " + safeName(name);
        note(lastGfxCommand);
        // CRYPTO_SYMMETRIC_KEY is binary, not a String, and must never enter
        // browser property events or downloadable diagnostics.
        if ("CRYPTO_SYMMETRIC_KEY".equals(name)) {
            synchronized (eventLock) {
                writeReply(GET_PROPERTY_CMD_TYPE, crypto.symmetricKeyReply());
            }
            note("native encrypted key reply sent (payload omitted)");
            return;
        }
        String value;
        synchronized (eventLock) {
            value = property(name);
            writeReply(GET_PROPERTY_CMD_TYPE, value.getBytes(StandardCharsets.ISO_8859_1));
        }
        if (!"GET_CACHED_AUTH".equals(name) && !"CRYPTO_PUBLIC_KEY".equals(name))
            enqueue("{\"type\":\"property\",\"name\":\"" + HttpUtil.json(name) + "\",\"value\":\"" + HttpUtil.json(value) + "\"}");
    }

    private String property(String name) {
        if ("GFX_TEXTMODE".equals(name)) return "";
        if ("GFX_BLENDMODE".equals(name)) return "PREMULTIPLY";
        if ("GFX_SCALING".equals(name)) return "HARDWARE";
        if ("GFX_OFFLINE_IMAGE_CACHE".equals(name)) return "FALSE";
        if ("OFFLINE_CACHE_CONTENTS".equals(name)) return "";
        if ("ADVANCED_IMAGE_CACHING".equals(name)) return "";
        if ("GFX_BITMAP_FORMAT".equals(name)) return "PNG,JPG,GIF,BMP";
        if ("GFX_COMPOSITE".equals(name)) return "BLEND";
        if ("GFX_SURFACES".equals(name) || "GFX_HIRES_SURFACES".equals(name)) return "TRUE";
        if ("GFX_YUV_IMAGE_CACHE".equals(name)) return unifiedGraphics ? "UNIFIED" : "";
        if ("GFX_DIFFUSE_TEXTURES".equals(name) || "GFX_XFORMS".equals(name) || "GFX_TEXTURE_BATCH_LIMIT".equals(name)) return "";
        if ("GFX_COLORKEY".equals(name)) return "080010";
        if ("STREAMING_PROTOCOLS".equals(name)) return "file,stv";
        if ("INPUT_DEVICES".equals(name)) return MiniClientCapabilityPolicy.inputDevices();
        if ("DISPLAY_OVERSCAN".equals(name)) return "0;0;1.0;1.0";
        if ("FIRMWARE_VERSION".equals(name)) return "9.0.0";
        if ("DETAILED_BUFFER_STATS".equals(name)) return "";
        if ("PUSH_BUFFER_SEEKING".equals(name)) return "TRUE";
        if ("FRAME_STEP".equals(name)) return "";
        if ("VIDEO_PLAYBACK_RATE".equals(name)) return "NATIVE_FORWARD_0.5_TO_2";
        if ("GFX_SUBTITLES".equals(name)) return "TRUE";
        if ("FORCED_MEDIA_RECONNECT".equals(name)) return "TRUE";
        if ("REMOTE_FS".equals(name)) return "";
        if ("GFX_VIDEO_UPDATE".equals(name)) return "";
        if ("ZLIB_COMM".equals(name)) return "";
        // Ordinary browser playback remains PULL/HLS. The P07 native DVD role can
        // advertise only the MPEG2-PS PUSH contract required by stock MiniDVDPlayer.
        if ("VIDEO_CODECS".equals(name)) return "H.264,MPEG2-VIDEO,MPEG1-VIDEO,HEVC";
        if ("AUDIO_CODECS".equals(name)) return "AAC,AC3,EAC3,MP2,MP3,FLAC,PCM";
        if ("PUSH_AV_CONTAINERS".equals(name)) return MiniClientCapabilityPolicy.pushContainers();
        if ("PULL_AV_CONTAINERS".equals(name)) return "MPEG2-PS,MPEG1-PS,MPEG2-TS,AVI,Quicktime,MATROSKA,MP3,AAC,AC3,FLAC,WAV";
        if ("MEDIA_PLAYER_BUFFER_DELAY".equals(name)) return "0";
        if ("FIXED_PUSH_MEDIA_FORMAT".equals(name) || "FIXED_PUSH_REMUX_FORMAT".equals(name)) return "";
        if ("CRYPTO_ALGORITHMS".equals(name)) return crypto.capabilities();
        if ("AUTH_CACHE".equals(name) || "GET_CACHED_AUTH".equals(name)) return "";
        if ("GFX_SUPPORTED_RESOLUTIONS".equals(name)) return width + "x" + height + ";windowed";
        if ("GFX_FIXED_PAR".equals(name)) return "";
        if ("GFX_RESOLUTION".equals(name)) return width + "x" + height;
        if ("GFX_DRAWMODE".equals(name)) return "FULLSCREEN";
        if ("VIDEO_ADVANCED_ASPECT".equals(name)) return "Source";
        if ("VIDEO_ADVANCED_ASPECT_LIST".equals(name)) return "Source;Fill;Zoom;4x3;16x9";
        if ("MEDIA_STATE_URL".equals(name)) return "TRUE";
        return "";
    }

    private void handleSetProperty(byte[] body, int len) throws IOException {
        // Lengths are unsigned 16-bit fields. Reject truncated/extra data
        // before interpreting either text values or the binary public key.
        if (len < 4) { writeReply(SET_PROPERTY_CMD_TYPE, intBytes(1)); return; }
        int nl = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
        int vl = ((body[2] & 0xff) << 8) | (body[3] & 0xff);
        if (nl == 0 || 4 + nl + vl != len) {
            note("malformed set-property lengths rejected");
            writeReply(SET_PROPERTY_CMD_TYPE, intBytes(1)); return;
        }
        String name = new String(body, 4, nl, StandardCharsets.ISO_8859_1);
        byte[] rawValue = java.util.Arrays.copyOfRange(body, 4 + nl, len);
        lastGfxCommand = "set property " + safeName(name);
        note(lastGfxCommand);
        if (name.startsWith("CRYPTO_")) {
            synchronized (eventLock) {
                // Atomic old-mode ACK + transition: concurrent input cannot
                // appear on the wrong side of the server's encryption toggle.
                boolean encryptAck = crypto.enabled();
                int result;
                if ("CRYPTO_PUBLIC_KEY".equals(name)) result = crypto.setPublicKey(rawValue);
                else if ("CRYPTO_ALGORITHMS".equals(name))
                    result = crypto.selectAlgorithms(new String(rawValue, StandardCharsets.ISO_8859_1));
                else if ("CRYPTO_EVENTS_ENABLE".equals(name)) {
                    String enabled = new String(rawValue, StandardCharsets.ISO_8859_1);
                    result = "TRUE".equalsIgnoreCase(enabled) ? crypto.setEnabled(true) :
                            "FALSE".equalsIgnoreCase(enabled) ? crypto.setEnabled(false) : 1;
                } else result = 1;
                writeReplyLocked(SET_PROPERTY_CMD_TYPE, intBytes(result), encryptAck);
                note("native " + safeName(name) + (result == 0 ? " accepted" : " rejected"));
            }
            return;
        }
        int result = 0;
        String value = new String(rawValue, StandardCharsets.ISO_8859_1);
        // Authentication is server/STV-owned. This plugin has no auth cache.
        if ("SET_CACHED_AUTH".equals(name)) result = 1;
        if ("ZLIB_COMM_XFER".equals(name) && "TRUE".equalsIgnoreCase(value)) result = 1;
        if ("ADVANCED_IMAGE_CACHING".equals(name) && "TRUE".equalsIgnoreCase(value)) result = 1;
        if ("RECONNECT_SUPPORTED".equals(name)) {
            reconnectAllowed = "TRUE".equalsIgnoreCase(value);
            note("native GFX reconnect negotiated " + reconnectAllowed);
        }
        if ("SUBTITLES_CALLBACKS".equals(name)) {
            subtitleCallbacks = "TRUE".equalsIgnoreCase(value);
            mediaBridge.setSubtitleCallbacksEnabled(subtitleCallbacks);
            note("native subtitle callbacks negotiated " + subtitleCallbacks);
        }
        if ("GFX_RESOLUTION".equals(name)) {
            java.util.regex.Matcher size = java.util.regex.Pattern.compile("^(\\d{3,4})x(\\d{3,4})(?:[pi].*)?$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value.trim());
            if (size.matches()) {
                int w=Integer.parseInt(size.group(1)),h=Integer.parseInt(size.group(2));
                if(w>=320&&w<=1920&&h>=240&&h<=1080){width=w;height=h;enqueue(resizeJson());}
                else result=1;
            } else result=1;
        } else if ("VIDEO_CC_STATE".equals(name)) {
            try { mediaBridge.setSageTvClosedCaptionState(Integer.parseInt(value.trim())); } catch (RuntimeException ignored) {}
            enqueue("{\"type\":\"captionState\",\"value\":\"" + HttpUtil.json(value) + "\",\"authority\":\"stv\"}");
        } else if ("VIDEO_ADVANCED_ASPECT".equals(name)) {
            enqueue("{\"type\":\"aspect\",\"value\":\"" + HttpUtil.json(value) + "\"}");
        } else if ("MENU_HINT".equals(name)) {
            enqueue("{\"type\":\"menuHint\",\"value\":\"" + HttpUtil.json(value) + "\"}");
        } else if ("GFX_ASPECT".equals(name)) {
            enqueue("{\"type\":\"uiAspect\",\"value\":\"" + HttpUtil.json(value) + "\"}");
        } else if ("VIBE_CURRENT_CHANNEL".equals(name)) {
            enqueue("{\"type\":\"channel\",\"value\":\"" + HttpUtil.json(value) + "\"}");
        }
        writeReply(SET_PROPERTY_CMD_TYPE, intBytes(result));
    }

    private void handleFs(byte[] body, int len) throws IOException {
        // Remote filesystem is intentionally not advertised. If an older STV asks
        // anyway, return NO_PERMISSIONS instead of leaving SageTV waiting.
        writeReply(FS_CMD_TYPE, intBytes(2));
    }

    private void writeReply(int type, byte[] payload) throws IOException {
        synchronized (eventLock) { writeReplyLocked(type, payload, crypto.enabled()); }
    }

    /** Caller must hold eventLock; keep headers and each entire body atomic. */
    private void writeReplyLocked(int type, byte[] payload, boolean encrypted) throws IOException {
        DataOutputStream out = eventOut;
        if (!alive || closed.get() || reconnecting || out == null)
            throw new IOException("Native GFX channel is not ready (" + phase + ")");
        // Property/GFX/FS replies (types < 128) are required during startup.
        // Keyboard, mouse and remote commands are not: discard early input
        // rather than replaying a click into a newly displayed login/menu.
        // Resize, repaint and media-update entry points coalesce separately.
        if (type >= 128 && !uiEventsReady) {
            droppedEarlyInput++;
            return;
        }
        try {
            int n = payload == null ? 0 : payload.length;
            // Compute before writing a header; crypto failure must not leave a
            // partial packet or fall back to plaintext. Header length stays n.
            byte[] wire = crypto.encode(payload, encrypted);
            out.write(type); out.write((n >> 16) & 0xff); out.write((n >> 8) & 0xff); out.write(n & 0xff);
            out.writeInt(0); out.writeInt(replyCount.getAndIncrement()); out.writeInt(0);
            if (wire.length > 0) out.write(wire);
            out.flush();
            if (type >= 128) { sentUiEvents++; lastUiEventType = type; }
        } catch (IOException e) {
            closeSocket(gfxSocket); throw e;
        }
    }

    private void mediaLoop() {
        mediaReady.countDown();
        while (alive) {
            Socket current = mediaSocket;
            try {
                DataInputStream in = new DataInputStream(current.getInputStream());
                OutputStream out = current.getOutputStream();
                while (alive) {
                    byte[] h = new byte[4]; in.readFully(h);
                    int cmd = h[0] & 0xff;
                    int len = ((h[1] & 0xff) << 16) | ((h[2] & 0xff) << 8) | (h[3] & 0xff);
                    byte[] body = new byte[len]; in.readFully(body);
                    lastMediaCommand = "media " + cmd + " (" + len + " bytes)";
                    byte[] reply = mediaBridge.handle(cmd, body);
                    if (reply != null && reply.length > 0) { out.write(reply); out.flush(); }
                    if (cmd == 1) break;
                }
            } catch (Exception e) {
                if (alive) { note("media I/O after " + lastMediaCommand + ": " + exceptionText(e)); enqueue(errorJson("Media connection: " + exceptionText(e))); }
            } finally {
                closeSocket(current);
                synchronized (lifecycleLock) { if (mediaSocket == current) mediaSocket = null; }
            }
            if (!alive) break;
            try {
                if (!installMedia(establish(1, 30000))) break;
                if (alive) enqueue(statusJson("media-reconnected"));
            } catch (Exception e) { if (alive) fail("Media reconnect failed: " + exceptionText(e)); break; }
        }
    }

    void sendSageCommand(int command) throws IOException {
        if (command < 2 || command > 108) throw new IllegalArgumentException("Invalid SageTV command");
        writeEventInt(SAGECOMMAND_EVENT_REPLY_TYPE, command);
    }

    void sendKey(int keyCode, int modifiers, int keyChar) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(10);
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(keyCode); data.writeChar((char) keyChar); data.writeInt(modifiers);
        writeReply(KB_EVENT_REPLY_TYPE, bytes.toByteArray());
    }

    void sendMouse(int type, int x, int y, int button, int modifiers, int clicks) throws IOException {
        if (type < 130 || type > 135) throw new IllegalArgumentException("Invalid mouse event type");
        if (button < 0 || button > 3) throw new IllegalArgumentException("Invalid mouse button");
        if (clicks < -127 || clicks > 127) throw new IllegalArgumentException("Invalid click/wheel count");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(14);
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(x); data.writeInt(y); data.writeInt(modifiers);
        data.writeByte(type == MWHEEL_EVENT_REPLY_TYPE ? clicks : Math.max(0, clicks)); data.writeByte(button);
        writeReply(type, bytes.toByteArray());
    }

    /** Keep a complete click atomic with respect to all other native writes. */
    void sendMouseClick(int x, int y, int button, boolean includeClick) throws IOException {
        if (button < 1 || button > 3) throw new IllegalArgumentException("Invalid click button");
        synchronized (eventLock) {
            sendMouse(133,x,y,0,0,0); sendMouse(130,x,y,button,0,1); sendMouse(131,x,y,button,0,1);
            if (includeClick) sendMouse(132,x,y,button,0,1);
        }
    }

    void sendText(String text) throws IOException {
        if (text == null) return;
        if (text.length() > 512) throw new IllegalArgumentException("Text is limited to 512 UTF-16 code units per request");
        synchronized (eventLock) {
            for (int i=0; i<text.length(); i++) { char ch=text.charAt(i); sendKey(ch=='\n'?13:0,0,ch); }
        }
    }

    void sendResize(int w, int h) throws IOException {
        if (w < 320 || w > 1920 || h < 240 || h > 1080) throw new IllegalArgumentException("UI size must be 320..1920 by 240..1080");
        synchronized (eventLock) {
            requireEventChannel();
            if (!uiEventsReady) {
                pendingWidth = w; pendingHeight = h; pendingResize = true;
                deferredUiEvents++;
                return;
            }
            sendResizeNow(w, h);
        }
    }

    /** Caller holds eventLock and has crossed the first-frame boundary. */
    private void sendResizeNow(int w, int h) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8);
        DataOutputStream data = new DataOutputStream(bytes); data.writeInt(w); data.writeInt(h);
        writeReply(UI_RESIZE_EVENT_REPLY_TYPE, bytes.toByteArray());
        width = w; height = h;
        enqueue(resizeJson());
        sendRepaintNow(0, 0, width, height);
    }

    private void requireEventChannel() throws IOException {
        if (!alive || closed.get() || reconnecting || eventOut == null)
            throw new IOException("Native GFX channel is not ready (" + phase + ")");
    }

    private String resizeJson() { return "{\"type\":\"resize\",\"width\":" + width + ",\"height\":" + height + "}"; }

    void sendRepaint(int x, int y, int w, int h) throws IOException {
        synchronized (eventLock) {
            requireEventChannel();
            if (!uiEventsReady) {
                pendingRepaint = true; deferredUiEvents++;
                return;
            }
            sendRepaintNow(x, y, w, h);
        }
    }

    private void sendRepaintNow(int x, int y, int w, int h) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16);
        DataOutputStream data = new DataOutputStream(bytes); data.writeInt(x); data.writeInt(y); data.writeInt(w); data.writeInt(h);
        writeReply(UI_REPAINT_EVENT_REPLY_TYPE, bytes.toByteArray());
    }

    private void writeEventInt(int type, int value) throws IOException { writeReply(type, intBytes(value)); }

    List<String> pollEvents(int max, long waitMs) throws InterruptedException {
        List<String> result = new ArrayList<String>();
        String first = events.poll(Math.max(0L, waitMs), TimeUnit.MILLISECONDS);
        if (first != null) result.add(first);
        events.drainTo(result, Math.max(0, max - result.size()));
        return result;
    }

    void reportBrowserState(String expected, long timeMs, int videoWidth, int videoHeight, float volume, boolean paused, boolean ended) {
        if (mediaBridge.reportBrowserState(expected,timeMs,videoWidth,videoHeight,volume,paused,ended)) {
            browserVideoWidth = videoWidth > 0 ? videoWidth : browserVideoWidth;
            browserVideoHeight = videoHeight > 0 ? videoHeight : browserVideoHeight;
        }
    }

    boolean recoverMedia(String expected, long timeMs) throws Exception { return mediaBridge.recoverFromBrowserPosition(expected,timeMs); }
    boolean seekMedia(String expected, long timeMs) throws Exception { return mediaBridge.seekFromBrowser(expected,timeMs); }

    void postSubtitleInfo(long pts45Khz,long duration45Khz,byte[] data,int flags) {
        if(!subtitleCallbacks)return;
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(14+(data==null?0:data.length));
            DataOutputStream out=new DataOutputStream(bytes);
            out.writeInt(flags);out.writeInt((int)pts45Khz);out.writeInt((int)duration45Khz);
            int n=data==null?0:data.length;out.writeShort(n);if(n>0)out.write(data);
            writeReply(SUBTITLE_UPDATE_REPLY_TYPE,bytes.toByteArray());
            subtitleCallbackEvents++;subtitleCallbackBytes+=n;
        } catch(Exception e) { note("subtitle callback failed: "+exceptionText(e)); }
    }

    boolean subtitleCallbacksEnabled(){return subtitleCallbacks;}
    long subtitleCallbackEvents(){return subtitleCallbackEvents;}
    long subtitleCallbackBytes(){return subtitleCallbackBytes;}

    void sendMediaPlayerUpdate() throws IOException {
        synchronized (eventLock) {
            requireEventChannel();
            if (!uiEventsReady) { pendingMediaUpdate = true; deferredUiEvents++; return; }
            writeReply(201, new byte[0]);
        }
    }

    int browserVideoWidth() { return browserVideoWidth; }
    int browserVideoHeight() { return browserVideoHeight; }

    void enqueue(String json) {
        if (json == null) return;
        try {
            // Backpressure, never drop an arbitrary texture/resize/draw command.
            // Losing one command leaves the browser's image-handle state corrupt.
            if (!events.offer(json, 2L, TimeUnit.SECONDS)) {
                lastError="Browser GFX queue overflow; reconnect required (no drawing commands silently dropped)";
                alive=false;events.clear();events.offer(fatalErrorJson(lastError));
                try { if(gfxSocket!=null) gfxSocket.close(); } catch(IOException ignored) {}
                try { if(mediaSocket!=null) mediaSocket.close(); } catch(IOException ignored) {}
            }
            eventQueuePeak=Math.max(eventQueuePeak,events.size());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    boolean hasEvents() { return !events.isEmpty(); }

    void fail(String message) {
        if (closed.get()) return;
        lastError = message == null ? "MiniClient session failed" : message;
        note(lastError); enqueue(fatalErrorJson(lastError)); close();
    }

    void supersede() { fail("This client ID was opened by another browser connection. Close the other tab before reconnecting."); }

    void close() {
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) return;
            alive = false; phase = "closed"; closedAt = System.currentTimeMillis();
            // Closing sockets first wakes blocked readers/handshakes even when
            // FFmpeg/player cleanup is waiting for its own lock.
            closeSocket(mediaSocket); closeSocket(gfxSocket);
            for (Socket s : openingSockets) closeSocket(s);
            mediaSocket = null; gfxSocket = null; gfxIn = null; eventOut = null;
        }
        synchronized (eventLock) {
            crypto.close();
            uiEventsReady = false;
            pendingResize = pendingRepaint = pendingMediaUpdate = false;
        }
        mediaBridge.close();
        note("closed"); enqueue(statusJson("closed"));
    }

    private static void closeSocket(Socket s) { if (s != null) try { s.close(); } catch (IOException ignored) {} }
    private static String exceptionText(Throwable e) { return e.getClass().getSimpleName() + ": " + compact(e.getMessage()); }
    private static String safeName(String name) { String safe = name.replaceAll("[^A-Za-z0-9_]", "?"); return safe.substring(0, Math.min(100, safe.length())); }
    private void note(String message) {
        synchronized (trace) {
            if (trace.size() >= 64) trace.remove(0);
            trace.add("{\"atMs\":" + System.currentTimeMillis() + ",\"event\":\"" + HttpUtil.json(message) + "\"}");
        }
    }

    String statusJson(String state) {
        return "{\"type\":\"status\",\"state\":\"" + HttpUtil.json(state) + "\",\"server\":\"" + HttpUtil.json(server + ":" + port) + "\",\"clientId\":\"" + HttpUtil.json(clientId) + "\",\"error\":\"" + HttpUtil.json(lastError) + "\"}";
    }
    private String errorJson(String message) { return "{\"type\":\"error\",\"message\":\"" + HttpUtil.json(message) + "\"}"; }
    private String fatalErrorJson(String message) { return "{\"type\":\"error\",\"fatal\":true,\"message\":\"" + HttpUtil.json(message) + "\"}"; }
    String json() {
        StringBuilder history = new StringBuilder("[");
        synchronized (trace) { for (String entry : trace) { if (history.length() > 1) history.append(','); history.append(entry); } }
        history.append(']');
        String cryptoStatus, startupStatus;
        synchronized (eventLock) {
            cryptoStatus = crypto.json();
            startupStatus = "{\"ready\":" + uiEventsReady + ",\"firstFrameCompleted\":" + firstFrameCompleted +
                ",\"deferredUiEvents\":" + deferredUiEvents + ",\"droppedEarlyInput\":" + droppedEarlyInput +
                ",\"pendingResize\":" + pendingResize + ",\"pendingRepaint\":" + pendingRepaint +
                ",\"pendingMediaUpdate\":" + pendingMediaUpdate + ",\"sentUiEvents\":" + sentUiEvents +
                ",\"lastUiEventType\":" + lastUiEventType + "}";
        }
        return "{\"session\":\"" + HttpUtil.json(id) + "\",\"alive\":" + alive + ",\"server\":\"" + HttpUtil.json(server + ":" + port) + "\",\"clientId\":\"" + HttpUtil.json(clientId) + "\",\"width\":" + width + ",\"height\":" + height + ",\"queuedEvents\":" + events.size() + ",\"peakQueuedEvents\":" + eventQueuePeak +
            ",\"threading\":{\"gfxReader\":" + (gfxThread!=null&&gfxThread.isAlive()) + ",\"mediaReader\":" + (mediaThread!=null&&mediaThread.isAlive()) + "},\"error\":\"" + HttpUtil.json(lastError) + "\",\"connection\":{\"phase\":\"" + phase +
            "\",\"firstFrameStarted\":" + firstFrameStarted + ",\"reconnectAllowed\":" + reconnectAllowed + ",\"reconnecting\":" + reconnecting + ",\"gfxPackets\":" + gfxFrames.get() +
            ",\"reconnectAttempts\":" + reconnectAttempts.get() + ",\"reconnectSuccesses\":" + reconnectSuccesses.get() + ",\"lastGfxCommand\":\"" + HttpUtil.json(lastGfxCommand) + "\",\"lastMediaCommand\":\"" + HttpUtil.json(lastMediaCommand) +
            "\",\"lastTransportError\":\"" + HttpUtil.json(lastTransportError) + "\",\"crypto\":" + cryptoStatus + ",\"startupEvents\":" + startupStatus + ",\"capabilities\":" + MiniClientCapabilityPolicy.json() + ",\"unifiedGraphics\":" + unifiedGraphics + ",\"gfxOpcodeLedger\":" + gfxOpcodeLedger.json() + ",\"dvd\":" + mediaBridge.dvdJson() + ",\"trace\":" + history + "}}";
    }
    private String shortId() { return id.substring(0, Math.min(8, id.length())); }

    private static int gfxArgInt(byte[] body, int length, int argumentOffset, int fallback) {
        int offset = 4 + argumentOffset;
        if (body == null || length < offset + 4 || body.length < offset + 4) return fallback;
        return readInt(body, offset);
    }

    private static byte[] intBytes(int v) { return new byte[] { (byte)(v >>> 24), (byte)(v >>> 16), (byte)(v >>> 8), (byte)v }; }
    static int readInt(byte[] b, int off) { return ((b[off] & 0xff) << 24) | ((b[off+1] & 0xff) << 16) | ((b[off+2] & 0xff) << 8) | (b[off+3] & 0xff); }
    static long readLong(byte[] b, int off) { long v=0; for(int i=0;i<8;i++) v=(v<<8)|(b[off+i]&0xffL); return v; }
    private static String compact(String s) { return s == null ? "unknown error" : s.replace('\r',' ').replace('\n',' ').trim(); }
    private static String normalizeClientId(String raw) {
        String hex = raw == null ? "" : raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        if (hex.length() < 12) hex = (hex + "020000000001").substring(0, 12);
        if (hex.length() > 12) hex = hex.substring(0, 12);
        StringBuilder b = new StringBuilder();
        for(int i=0;i<12;i+=2){ if(b.length()>0)b.append(':'); b.append(hex.substring(i,i+2)); }
        return b.toString();
    }
    private static byte[] clientIdBytes(String raw) {
        String hex = raw.replaceAll("[^0-9A-Fa-f]", ""); byte[] b = new byte[6];
        for(int i=0;i<6;i++) try { b[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16); } catch(Exception ignored) {}
        return b;
    }
}
