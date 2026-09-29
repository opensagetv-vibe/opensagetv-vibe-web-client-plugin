package org.opensagetv.webplayer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import static org.opensagetv.webplayer.MiniClientRecoverySmoke.*;

/** Real TCP and JCA crypto, but a synthetic SageTV server; no live server claims. */
public final class MiniClientAuthenticationSmoke {
    private static int count;
    private static final byte[] EMPTY = new byte[0];
    private static byte[] latin(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }
    private static final class Wire {
        final Socket socket;
        boolean encrypted;
        Cipher decrypt;
        byte[] rawKey, wrappedKey;
        int sequence = -1, keyboardEvents, encryptedPackets;
        Wire(Socket s) { socket = s; }
        byte[] read(int expected) throws Exception {
            while (true) {
                byte[] h = bytes(socket.getInputStream(), 16);
                int type = h[0] & 255, n = ((h[1]&255)<<16)|((h[2]&255)<<8)|(h[3]&255);
                int seq = MiniClientSession.readInt(h,8);
                if(sequence>=0) check(seq==sequence+1,"reply sequence was not atomic");
                sequence=seq;
                check(n<100000,"corrupt header/length after crypto transition");
                int wireLen=encrypted&&n>0 ? ((n/8)+1)*8 : n;
                byte[] wire = bytes(socket.getInputStream(), wireLen);
                byte[] plain=encrypted&&n>0 ? decrypt.doFinal(wire) : wire;
                if(encrypted&&n>0) {check(!Arrays.equals(wire,plain),"plaintext while encryption active");encryptedPackets++;}
                check(plain.length==n,"header did not retain plaintext length");
                if(type==129) keyboardEvents++;
                if(type==expected) return plain;
                check(type==129,"unexpected event "+type+" waiting for "+expected);
                check(n==10 && MiniClientSession.readInt(plain,0)==0,"concurrent key event corrupted");
            }
        }
        byte[] get(String name)throws Exception { frame(socket,0,latin(name));return read(0); }
        int set(String name,String value)throws Exception { return set(name,latin(value)); }
        int set(String name,byte[] value)throws Exception {
            ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
            d.writeShort(name.length());d.writeShort(value.length);d.write(latin(name));d.write(value);frame(socket,1,b.toByteArray());
            return integer(read(1));
        }
        void toggle(boolean next)throws Exception { check(set("CRYPTO_EVENTS_ENABLE",next?"TRUE":"FALSE")==0,"toggle rejected");encrypted=next; }
        void negotiate(int bits)throws Exception {
            check(new String(get("CRYPTO_ALGORITHMS"),StandardCharsets.ISO_8859_1).equals("RSA,Blowfish"),"client advertises no usable native crypto");
            check(set("CRYPTO_ALGORITHMS","RSA,Blowfish")==0,"algorithm rejected");
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(bits);KeyPair server=generator.generateKeyPair();
            byte[] pub=server.getPublic().getEncoded();check(set("CRYPTO_PUBLIC_KEY",pub)==0,"binary X509 key rejected");
            wrappedKey=get("CRYPTO_SYMMETRIC_KEY");check(wrappedKey.length==(bits+7)/8,"not a raw RSA ciphertext reply");
            check(Arrays.equals(wrappedKey,get("CRYPTO_SYMMETRIC_KEY")),"repeated key request changed key");
            Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.DECRYPT_MODE,server.getPrivate());rawKey=rsa.doFinal(wrappedKey);
            check(rawKey.length==16,"expected 128-bit Blowfish key");
            decrypt=Cipher.getInstance("Blowfish/ECB/PKCS5Padding");decrypt.init(Cipher.DECRYPT_MODE,new SecretKeySpec(rawKey,"Blowfish"));
            toggle(true);
        }
        void firstFrame()throws Exception {draw(socket,31);draw(socket,30);check(integer(read(16))==0,"encrypted flip reply");read(192);read(193);}
    }
    private static Wire connected(Env e)throws Exception {e.accept(1,2);Socket g=e.accept(0,2);init(g,true,false);return new Wire(g);}
    private static void hold(Env e)throws Exception {e.signal.countDown();check(e.release.await(5,TimeUnit.SECONDS),"test client release timeout");}
    private static void inspect(Env e)throws Exception {e.session.start();check(e.signal.await(8,TimeUnit.SECONDS),"server handshake/test did not complete");check(e.session.isAlive(),e.session.json());e.release.countDown();}
    private static void test(String name,Work server,Work client)throws Exception {run(name,server,client);count++;}
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("--expect-unsupported")) {
            legacyInitEvents = true;
            test("baseline 3.2.1 reproduces CRYPTO_ALGORITHMS rejection before first frame",e->{Wire w=connected(e);
                check(w.get("CRYPTO_ALGORITHMS").length==0,"baseline unexpectedly supports crypto");reset(w.socket);e.noNewConnection();
            },e->{e.session.start();await(()->!e.session.isAlive(),"baseline did not fail");String j=e.session.json();
                check(j.contains("get property CRYPTO_ALGORITHMS")&&j.contains("\"firstFrameStarted\":false")&&j.contains("\"reconnectAttempts\":0"),j);});
            return;
        }
        test("ordinary no-auth startup remains plaintext after capability query",e->{Wire w=connected(e);
            check(new String(w.get("CRYPTO_ALGORITHMS"),StandardCharsets.ISO_8859_1).equals("RSA,Blowfish"),"capability");w.firstFrame();
            check(w.get("GFX_RESOLUTION").length>0,"plaintext property failed");hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        for(int bits:new int[]{1024,2048})test("RSA-"+bits+" binary key exchange, encrypted properties, first GFX frame",e->{Wire w=connected(e);w.negotiate(bits);w.firstFrame();
            check(new String(w.get("GFX_RESOLUTION"),StandardCharsets.ISO_8859_1).equals("640x360"),"encrypted property mismatch");hold(e);
        },e->{e.session.start();check(e.signal.await(8,TimeUnit.SECONDS),"crypto setup incomplete");String j=e.session.json();
            check(j.contains("\"eventsEncrypted\":true")&&j.contains("\"keyEstablished\":true")&&j.contains("\"firstFrameStarted\":true"),j);e.release.countDown();});
        test("premature enable and missing public key rejected without false success",e->{Wire w=connected(e);
            check(w.set("CRYPTO_EVENTS_ENABLE","TRUE")==1,"enabled before keys");check(w.get("CRYPTO_SYMMETRIC_KEY").length==0,"key without server key");
            check(w.set("CRYPTO_PUBLIC_KEY",new byte[]{0,1,2,(byte)255})==1,"out-of-order key accepted");w.negotiate(1024);w.firstFrame();hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        test("unsupported algorithms, invalid RSA key, malformed boolean, unknown crypto rejected",e->{Wire w=connected(e);
            check(w.set("CRYPTO_ALGORITHMS","DH,DES")==1,"unsupported downgrade accepted");check(w.set("CRYPTO_ALGORITHMS","RSA,Blowfish")==0,"rsa rejected");
            check(w.set("CRYPTO_PUBLIC_KEY",new byte[]{0,0,(byte)255,1})==1,"bad X509 accepted");
            check(w.set("CRYPTO_EVENTS_ENABLE","perhaps")==1,"bad toggle accepted");check(w.set("CRYPTO_FUTURE","TRUE")==1,"unknown crypto accepted");w.negotiate(1024);hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        test("truncated and mismatched property lengths rejected without changing mode",e->{Wire w=connected(e);
            frame(w.socket,1,new byte[]{0,20,0,1,65});check(integer(w.read(1))==1,"truncated property accepted");
            frame(w.socket,1,new byte[]{0,0,0,0,42});check(integer(w.read(1))==1,"extra bytes accepted");
            frame(w.socket,1,new byte[]{0,1});check(integer(w.read(1))==1,"short header accepted");w.negotiate(1024);hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        test("all outbound families use correct padding; empty payload stays empty",e->{Wire w=connected(e);w.negotiate(1024);w.firstFrame();
            check(w.get("UNKNOWN_EMPTY_PROPERTY").length==0,"empty encrypted property got padding");
            // Exercise actual public session input methods, not just crypto helpers.
            e.session.sendSageCommand(20);check(integer(w.read(136))==20,"sage command corrupted");
            e.session.sendKey(0,0,'Q');check(w.read(129)[5]=='Q',"key char corrupted");
            e.session.sendMouse(130,55,60,1,0,1);check(w.read(130).length==14,"mouse length");
            e.session.sendResize(800,600);check(w.read(192).length==8,"resize length");check(w.read(193).length==16,"repaint length");
            e.session.sendMediaPlayerUpdate();check(w.read(201).length==0,"empty event received padding");
            frame(w.socket,2,EMPTY);check(integer(w.read(2))==2,"filesystem reply not encrypted");
            check(w.encryptedPackets>=6,"not enough encrypted families");hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        test("enable/disable ACK uses old mode atomically with concurrent keyboard input",e->{Wire w=connected(e);w.negotiate(1024);w.firstFrame();
            AtomicBoolean active=new AtomicBoolean(true);AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread sender=new Thread(()->{try{for(int i=0;i<400&&active.get();i++){e.session.sendKey(0,0,65+i%26);Thread.sleep(1);}}catch(Throwable x){failure.set(x);}},"concurrent-auth-input");
            sender.start();try{for(int i=0;i<20;i++){w.toggle(false);Thread.sleep(3);w.toggle(true);Thread.sleep(3);}w.get("GFX_RESOLUTION");}
            finally{active.set(false);sender.join(2000);}
            check(failure.get()==null,"input writer failed: "+failure.get());check(w.keyboardEvents>10,"insufficient concurrent input");hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        test("active encrypted link rejects rekey/downgrade and keeps current cipher",e->{Wire w=connected(e);w.negotiate(1024);
            check(w.set("CRYPTO_ALGORITHMS","RSA,Blowfish")==1,"midstream rekey accepted");check(w.set("CRYPTO_PUBLIC_KEY",new byte[8])==1,"midstream key replacement accepted");
            check(w.get("GFX_RESOLUTION").length>0,"rejected rekey broke existing cipher");hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        test("keys and auth payloads excluded from browser events and diagnostic values",e->{Wire w=connected(e);w.negotiate(1024);
            check(w.get("GET_CACHED_AUTH").length==0,"invented cached credential");
            check(w.set("SET_CACHED_AUTH","private-token-never-store-or-export")==1,"unsupported auth cache accepted");
            String report=e.session.json()+e.session.pollEvents(1000,0).toString();
            check(!report.contains("private-token-never")&&!report.contains(Base64.getEncoder().encodeToString(w.rawKey))&&!report.contains(Base64.getEncoder().encodeToString(w.wrappedKey)),"credential/key leaked");
            check(!report.contains("\"name\":\"CRYPTO_SYMMETRIC_KEY\"")&&!report.contains("\"name\":\"GET_CACHED_AUTH\""),"secret property forwarded to browser");hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        test("reset while encrypted closes without unsafe type-5 resumption",e->{Wire w=connected(e);w.negotiate(1024);w.firstFrame();reset(w.socket);e.noNewConnection();
        },e->{e.session.start();await(()->!e.session.isAlive(),"encrypted reset did not close");await(()->e.session.json().contains("\"encryptedAtClose\":true"),"missing close crypto snapshot");
            check(e.session.json().contains("\"reconnectAttempts\":0"),e.session.json());});
        test("type-5 recovery allowed again after server disables encryption",e->{Wire w=connected(e);w.negotiate(1024);w.firstFrame();w.toggle(false);reset(w.socket);
            Socket next=e.accept(5,2);Wire resumed=new Wire(next);check(resumed.get("GFX_RESOLUTION").length>0,"plaintext recovery failed");hold(e);
        },e->{e.session.start();check(e.signal.await(8,TimeUnit.SECONDS),"no recovery");check(e.session.json().contains("\"reconnectSuccesses\":1"),e.session.json());e.release.countDown();});
        AtomicReference<byte[]> previous=new AtomicReference<>();
        for(int session=0;session<2;session++)test("fresh secret for independent session "+(session+1),e->{Wire w=connected(e);w.negotiate(1024);
            byte[] old=previous.getAndSet(w.rawKey.clone());if(old!=null)check(!Arrays.equals(old,w.rawKey),"secret reused across sessions");hold(e);
        },MiniClientAuthenticationSmoke::inspect);
        System.out.println("Native authentication tests: "+count+" PASS (real RSA/Blowfish + loopback TCP; synthetic server)");
    }
}
