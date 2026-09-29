package org.opensagetv.webplayer;

/** Centralizes stock-role capabilities whose advertisement changes Core behavior. */
final class MiniClientCapabilityPolicy {
    private static final String DVD_ROLE_PROPERTY = "sagetv.webplayer.nativeDvdProtocol";
    private MiniClientCapabilityPolicy() {}

    /**
     * P07 supplied the native wire/input backend and P08/P09 completed visible
     * browser DVD A/V, still-frame and SPU handling. Native DVD therefore ships
     * enabled by default. The JVM property remains an explicit troubleshooting
     * escape hatch: set it to false to restore the ordinary browser media role.
     */
    static boolean nativeDvdRoleEnabled() {
        return Boolean.parseBoolean(System.getProperty(DVD_ROLE_PROPERTY, "true"));
    }

    static String inputDevices() {
        // Stock VideoFrame selects MiniDVDPlayer only when INPUT_DEVICES omits
        // MOUSE. TOUCH remains advertised and the browser's explicit mouse
        // event endpoint continues to work; this is a role flag, not an event gate.
        return nativeDvdRoleEnabled() ? "IR,KEYBOARD,TOUCH,TV" : "IR,KEYBOARD,MOUSE,TOUCH,TV";
    }

    static String pushContainers() {
        return nativeDvdRoleEnabled() ? "MPEG2-PS" : "NONE";
    }

    static String json() {
        return "{\"nativeDvdRoleEnabled\":"+nativeDvdRoleEnabled()+
                ",\"inputDevices\":\""+inputDevices()+"\",\"pushContainers\":\""+pushContainers()+"\"}";
    }
}
