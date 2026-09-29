package org.opensagetv.webplayer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Auditable browser disposition for SageTV MiniClient GFX opcodes. */
final class GfxOpcodeLedger {
    enum Disposition { IMPLEMENTED, NEGOTIATED_OFF, UNKNOWN }

    private final Map<Integer,Integer> counts = new LinkedHashMap<Integer,Integer>();
    private int negotiatedOffCount;
    private int unknownCount;
    private int lastNegotiatedOff = -1;
    private int lastUnknown = -1;

    synchronized Disposition encounter(int opcode) {
        Integer old = counts.get(opcode);
        counts.put(opcode, old == null ? 1 : old + 1);
        Disposition disposition = disposition(opcode);
        if (disposition == Disposition.NEGOTIATED_OFF) {
            negotiatedOffCount++;
            lastNegotiatedOff = opcode;
        } else if (disposition == Disposition.UNKNOWN) {
            unknownCount++;
            lastUnknown = opcode;
        }
        return disposition;
    }

    static Disposition disposition(int opcode) {
        switch (opcode) {
            case 1: case 2:
            case 16: case 17: case 18: case 19: case 20: case 21: case 22:
            case 24: case 25: case 26: case 27:
            case 30: case 31: case 32: case 33: case 34: case 35:
            case 37: case 38: case 45: case 46:
            case 130: case 131:
                return Disposition.IMPLEMENTED;
            // These families are intentionally disabled by the matching
            // negotiated properties. Receiving one is diagnostic evidence,
            // not permission to pretend the operation worked.
            case 23: case 28: case 29: case 36: // text/font mode
            case 40:                           // diffuse textures
            case 41: case 42:                  // transforms
            case 43:                           // texture batches
            case 44:                           // offline image cache
                return Disposition.NEGOTIATED_OFF;
            default:
                return Disposition.UNKNOWN;
        }
    }

    static String name(int opcode) {
        switch (opcode) {
            case 1:return "INIT";case 2:return "DEINIT";case 16:return "DRAWRECT";
            case 17:return "FILLRECT";case 18:return "CLEARRECT";case 19:return "DRAWOVAL";
            case 20:return "FILLOVAL";case 21:return "DRAWROUNDRECT";case 22:return "FILLROUNDRECT";
            case 23:return "DRAWTEXT";case 24:return "DRAWTEXTURED";case 25:return "DRAWLINE";
            case 26:return "LOADIMAGE";case 27:return "UNLOADIMAGE";case 28:return "LOADFONT";
            case 29:return "UNLOADFONT";case 30:return "FLIPBUFFER";case 31:return "STARTFRAME";
            case 32:return "LOADIMAGELINE";case 33:return "PREPIMAGE";case 34:return "LOADIMAGECOMPRESSED";
            case 35:return "XFMIMAGE";case 36:return "LOADFONTSTREAM";case 37:return "CREATESURFACE";
            case 38:return "SETTARGETSURFACE";case 40:return "DRAWTEXTUREDDIFFUSED";
            case 41:return "PUSHTRANSFORM";case 42:return "POPTRANSFORM";case 43:return "TEXTUREBATCH";
            case 44:return "LOADCACHEDIMAGE";case 45:return "LOADIMAGETARGETED";case 46:return "PREPIMAGETARGETED";
            case 130:return "SETVIDEOPROP";case 131:return "MEDIA_RECONNECT";default:return "UNKNOWN_"+opcode;
        }
    }

    synchronized String json() {
        StringBuilder seen = new StringBuilder("{");
        for (Map.Entry<Integer,Integer> entry : counts.entrySet()) {
            if (seen.length() > 1) seen.append(',');
            seen.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        seen.append('}');
        return "{\"seen\":" + seen +
                ",\"negotiatedOffCount\":" + negotiatedOffCount +
                ",\"unknownCount\":" + unknownCount +
                ",\"lastNegotiatedOff\":" + lastNegotiatedOff +
                ",\"lastUnknown\":" + lastUnknown + "}";
    }
}
