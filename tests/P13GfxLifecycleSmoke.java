package org.opensagetv.webplayer;

import java.lang.reflect.Method;

public final class P13GfxLifecycleSmoke {
    private static int tests;
    private static void ok(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS "+name);tests++;}
    private static Object prop(MiniClientSession s,String name)throws Exception{
        Method m=MiniClientSession.class.getDeclaredMethod("property",String.class);m.setAccessible(true);return m.invoke(s,name);
    }
    public static void main(String[] args)throws Exception{
        ok(GfxOpcodeLedger.disposition(16)==GfxOpcodeLedger.Disposition.IMPLEMENTED,"primitive draw opcode is implemented");
        ok(GfxOpcodeLedger.disposition(41)==GfxOpcodeLedger.Disposition.NEGOTIATED_OFF,"transform opcode is explicitly negotiated off");
        ok(GfxOpcodeLedger.disposition(222)==GfxOpcodeLedger.Disposition.UNKNOWN,"unknown opcode is auditable rather than silently classified");
        GfxOpcodeLedger ledger=new GfxOpcodeLedger();ledger.encounter(16);ledger.encounter(41);ledger.encounter(222);String json=ledger.json();
        ok(json.contains("\"41\":1")&&json.contains("\"unknownCount\":1"),"opcode ledger retains encountered unsupported evidence");
        MiniClientSession off=new MiniClientSession("off","127.0.0.1","020000000001",1280,720,false);
        MiniClientSession on=new MiniClientSession("on","127.0.0.1","020000000002",1280,720,true);
        ok("".equals(prop(off,"GFX_YUV_IMAGE_CACHE")),"unified graphics is disabled by default");
        ok("UNIFIED".equals(prop(on,"GFX_YUV_IMAGE_CACHE")),"explicit session opt-in advertises UNIFIED");
        ok("".equals(prop(on,"GFX_XFORMS"))&&"".equals(prop(on,"GFX_TEXTURE_BATCH_LIMIT")),"unsupported transform/batch families remain negotiated off");
        ok(on.json().contains("\"unifiedGraphics\":true")&&on.json().contains("\"gfxOpcodeLedger\""),"session diagnostics expose unified state and opcode ledger");
        System.out.println("P13 GFX/lifecycle: "+tests+" tests PASS");
    }
}
