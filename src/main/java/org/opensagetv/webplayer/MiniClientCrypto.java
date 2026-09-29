package org.opensagetv.webplayer;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * SageTV's legacy native event-channel encryption, not browser HTTPS.
 *
 * Wire behavior follows Vibe MiniClientConnection (RSA + Blowfish branch):
 * server supplies X.509 RSA key; client returns RSA/PKCS1-encrypted random
 * Blowfish key; only nonempty reply/event bodies are encrypted. Headers keep
 * their PLAINTEXT length. Enable/disable acknowledgement uses the OLD mode.
 * All accesses are serialized by MiniClientSession.eventLock, including mode
 * transitions and complete socket writes. No keys or auth tokens are logged.
 *
 * These algorithms are mandated by the legacy protocol, not recommended for
 * a new protocol. Use trusted networking and HTTPS for the browser endpoint.
 */
final class MiniClientCrypto {
    private static final String AVAILABLE = discover();
    private PublicKey serverPublicKey;
    private byte[] encryptedSecretKey;
    private Cipher eventCipher;
    private String algorithms = "";
    private boolean enabled, everEnabled, enabledAtClose, keyWasEstablished;
    private boolean queried;
    private String stage = "not-requested", failure = "";
    private long encryptedMessages;

    private static String discover() {
        try {
            KeyFactory.getInstance("RSA");
            Cipher.getInstance("RSA/ECB/PKCS1Padding");
            KeyGenerator.getInstance("Blowfish");
            Cipher.getInstance("Blowfish/ECB/PKCS5Padding");
            return "RSA,Blowfish";
        } catch (GeneralSecurityException unavailable) { return ""; }
    }

    String capabilities() {
        queried = true;
        if ("not-requested".equals(stage)) stage = AVAILABLE.isEmpty() ? "unavailable" : "advertised";
        return AVAILABLE;
    }

    int selectAlgorithms(String requested) {
        // No silent algorithm downgrade, rekey or cipher replacement midstream.
        if (enabled || AVAILABLE.isEmpty() || requested == null ||
                !"RSA,Blowfish".equalsIgnoreCase(requested.replace(" ", "").trim())) {
            failure = "Unsupported or out-of-order crypto algorithm selection"; return 1;
        }
        clearKeys(); algorithms = "RSA,Blowfish"; stage = "algorithms-selected"; failure = "";
        return 0;
    }

    int setPublicKey(byte[] encoded) {
        if (enabled || !"RSA,Blowfish".equals(algorithms)) {
            failure = "Public key received before algorithm selection or while encrypted"; return 1;
        }
        clearKeys();
        try {
            PublicKey candidate = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
            int bits = ((RSAPublicKey) candidate).getModulus().bitLength();
            // Stock SageTV supplies 1024 bits; also accept newer server keys.
            if (bits < 1024 || bits > 8192) throw new GeneralSecurityException("RSA key size");
            serverPublicKey = candidate; stage = "public-key-accepted"; failure = "";
            return 0;
        } catch (GeneralSecurityException | ClassCastException invalid) {
            failure = "Invalid/unsupported server RSA public key"; stage = "key-rejected"; return 1;
        }
    }

    byte[] symmetricKeyReply() throws IOException {
        if (encryptedSecretKey != null) return encryptedSecretKey.clone();
        if (serverPublicKey == null) {
            failure = "Symmetric key requested before a valid public key";
            return new byte[0];
        }
        byte[] raw = null;
        try {
            KeyGenerator gen = KeyGenerator.getInstance("Blowfish");
            gen.init(128); // fresh JCA SecureRandom-backed key per negotiation/session
            SecretKey key = gen.generateKey();
            raw = key.getEncoded();
            Cipher wrap = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            wrap.init(Cipher.ENCRYPT_MODE, serverPublicKey);
            byte[] wrapped = wrap.doFinal(raw);
            Cipher events = Cipher.getInstance("Blowfish/ECB/PKCS5Padding");
            events.init(Cipher.ENCRYPT_MODE, key);
            encryptedSecretKey = wrapped; eventCipher = events;
            keyWasEstablished = true; stage = "key-established"; failure = "";
            return encryptedSecretKey.clone();
        } catch (GeneralSecurityException e) {
            failure = "Native encryption key setup failed";
            throw new IOException(failure + " (" + e.getClass().getSimpleName() + ")");
        } finally { if (raw != null) Arrays.fill(raw, (byte) 0); }
    }

    int setEnabled(boolean requested) {
        if (requested && eventCipher == null) {
            failure = "Event encryption requested before key setup"; return 1;
        }
        enabled = requested;
        everEnabled |= requested;
        stage = requested ? "events-encrypted" : "events-disabled-by-server";
        failure = ""; return 0;
    }

    boolean enabled() { return enabled; }

    byte[] encode(byte[] plain, boolean encrypt) throws IOException {
        if (plain == null || plain.length == 0) return new byte[0];
        if (!encrypt) return plain;
        if (eventCipher == null) throw new IOException("Native event cipher is not ready; refusing plaintext fallback");
        try {
            byte[] result = eventCipher.doFinal(plain);
            encryptedMessages++;
            return result;
        } catch (GeneralSecurityException e) {
            failure = "Native event encryption failed";
            throw new IOException(failure + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void clearKeys() {
        serverPublicKey = null; eventCipher = null;
        if (encryptedSecretKey != null) Arrays.fill(encryptedSecretKey, (byte) 0);
        encryptedSecretKey = null;
    }

    void close() {
        enabledAtClose = enabled; enabled = false; clearKeys();
    }

    String json() {
        return "{\"capabilityQueried\":" + queried + ",\"availableAlgorithms\":\"" + AVAILABLE +
                "\",\"selectedAlgorithms\":\"" + algorithms + "\",\"stage\":\"" + stage +
                "\",\"keyEstablished\":" + keyWasEstablished + ",\"eventsEncrypted\":" + enabled +
                ",\"everEnabled\":" + everEnabled + ",\"encryptedAtClose\":" + enabledAtClose +
                ",\"encryptedMessages\":" + encryptedMessages + ",\"error\":\"" + HttpUtil.json(failure) + "\"}";
    }
}
