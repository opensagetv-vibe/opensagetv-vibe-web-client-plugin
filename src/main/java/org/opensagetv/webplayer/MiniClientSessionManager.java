package org.opensagetv.webplayer;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Browser MiniClient session registry.
 *
 * Protocol behavior is derived from the Vibe Android MiniClient core
 * (MiniClientConnection/GFXCMD2/MediaCmd). The browser talks only to Jetty;
 * this class owns the real SageTV MiniClient sockets on behalf of the browser.
 */
final class MiniClientSessionManager {
    private static final Map<String, MiniClientSession> SESSIONS = new ConcurrentHashMap<String, MiniClientSession>();

    // Serialize replacements of the same native client ID, without blocking all
    // browser sessions on one network handshake. Failed sessions remain briefly
    // available to the diagnostics endpoint.
    private static final Object[] START_LOCKS = new Object[32];
    static { for (int i=0; i<START_LOCKS.length; i++) START_LOCKS[i] = new Object(); }
    private MiniClientSessionManager() {}

    static MiniClientSession start(String server, String clientId, int width, int height) throws IOException {
        return start(server,clientId,width,height,StreamOptions.defaults(),false);
    }
    static MiniClientSession start(String server,String clientId,int width,int height,StreamOptions options) throws IOException {
        return start(server,clientId,width,height,options,false);
    }
    static MiniClientSession start(String server,String clientId,int width,int height,StreamOptions options,boolean unifiedGraphics) throws IOException {
        String id = UUID.randomUUID().toString().replace("-", "");
        MiniClientSession session = new MiniClientSession(id, server, clientId, width, height, unifiedGraphics);
        session.media().configure(options);
        String key = session.identityKey();
        synchronized (START_LOCKS[(key.hashCode() & 0x7fffffff) % START_LOCKS.length]) {
            long cutoff = System.currentTimeMillis() - 10 * 60 * 1000L;
            for (Map.Entry<String, MiniClientSession> entry : SESSIONS.entrySet()) {
                MiniClientSession old = entry.getValue();
                if (old.isAlive() && key.equals(old.identityKey())) old.supersede();
                if (old.closedAt() > 0L && old.closedAt() < cutoff) SESSIONS.remove(entry.getKey(), old);
            }
            SESSIONS.put(id, session);
            try { session.start(); return session; }
            catch (IOException e) { SESSIONS.remove(id); session.close(); throw e; }
        }
    }

    static MiniClientSession get(String id) {
        return id == null ? null : SESSIONS.get(id);
    }

    static void stopAll(){for(String id:SESSIONS.keySet())stop(id);}

    static boolean stop(String id) {
        MiniClientSession session = SESSIONS.remove(id);
        if (session == null) return false;
        session.close();
        return true;
    }
}
