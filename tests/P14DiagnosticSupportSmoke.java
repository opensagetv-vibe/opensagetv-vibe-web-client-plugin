package org.opensagetv.webplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class P14DiagnosticSupportSmoke {
    private static int tests;
    private static void ok(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS "+name);tests++;}
    private static Map<String,byte[]> unzip(byte[] zip)throws Exception{
        Map<String,byte[]> out=new LinkedHashMap<String,byte[]>();
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip),StandardCharsets.UTF_8)){
            for(ZipEntry e;(e=in.getNextEntry())!=null;){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];for(int n;(n=in.read(buf))>0;)b.write(buf,0,n);out.put(e.getName(),b.toByteArray());}
        }
        return out;
    }
    private static void deleteTree(Path root)throws Exception{if(root==null||!Files.exists(root))return;List<Path> paths=new ArrayList<Path>();Files.walk(root).forEach(paths::add);Collections.sort(paths,Collections.reverseOrder());for(Path p:paths)Files.deleteIfExists(p);}
    public static void main(String[] args)throws Exception{
        String sanitized=DiagnosticSupport.sanitize("{\"password\":\"pw\",\"Authorization\":\"Bearer abc\",\"subtitleText\":\"hello\",\"count\":7}");
        ok(!sanitized.contains("Bearer abc")&&!sanitized.contains("hello")&&!sanitized.contains("\"pw\""),"server sanitizer removes credentials and subtitle content");
        ok(sanitized.contains("[redacted]")&&sanitized.contains("[omitted-content]")&&sanitized.contains("\"count\":7"),"server sanitizer retains safe counters");
        byte[] zip=DiagnosticSupport.zip("{\"password\":\"pw\",\"counter\":3}","{\"sessionKey\":\"deadbeef\",\"queuedEvents\":2}","{\"captionText\":\"secret\",\"segments\":9}","{\"available\":true}","{\"result\":\"PASS\"}");
        Map<String,byte[]> entries=unzip(zip);ok(entries.containsKey("report.json")&&entries.containsKey("hashes.sha256")&&entries.containsKey("README.txt"),"diagnostic ZIP contains report/hash/readme entries");
        String report=new String(entries.get("report.json"),StandardCharsets.UTF_8),hashes=new String(entries.get("hashes.sha256"),StandardCharsets.UTF_8);
        ok(!report.contains("deadbeef")&&!report.contains("secret")&&!report.contains("\"pw\"")&&report.contains("\"segments\":9"),"ZIP report is redacted without dropping safe stream counters");
        ok(hashes.contains(DiagnosticSupport.sha256(entries.get("report.json"))+"  report.json"),"ZIP hash matches report bytes");

        String oldEnabled=System.getProperty("sagetv.webplayer.diagnostics.writeEnabled"),oldDir=System.getProperty("sagetv.webplayer.diagnostics.dir"),oldTmp=System.getProperty("java.io.tmpdir");
        Path root=Files.createTempDirectory("p14-support-");
        try{
            System.setProperty("java.io.tmpdir",root.resolve("tmp").toString());
            System.clearProperty("sagetv.webplayer.diagnostics.dir");System.clearProperty("sagetv.webplayer.diagnostics.writeEnabled");
            DiagnosticSupport.SaveResult disabled=DiagnosticSupport.save(zip);ok("not-requested".equals(disabled.state),"external report writes are default-off");
            Path dest=root.resolve("diagnostic-destination");System.setProperty("sagetv.webplayer.diagnostics.dir",dest.toString());System.setProperty("sagetv.webplayer.diagnostics.writeEnabled","true");
            DiagnosticSupport.TestResult cancelledTest=DiagnosticSupport.testDestination(new AtomicBoolean(true));ok(!cancelledTest.ok&&"cancelled".equals(cancelledTest.stage),"destination test supports cooperative cancellation before creating a probe");
            DiagnosticSupport.TestResult test=DiagnosticSupport.testDestination();ok(test.ok&&"complete".equals(test.stage),"configured destination passes bounded write/read/hash/delete test");
            try(java.util.stream.Stream<Path> stream=Files.list(dest)){ok(stream.noneMatch(p->p.getFileName().toString().startsWith(".sagetv-webplayer-test-")),"destination test deletes only its own temporary probe");}
            DiagnosticSupport.SaveResult saved=DiagnosticSupport.save(zip);ok("external".equals(saved.state)&&Files.isRegularFile(dest.resolve(saved.fileName)),"report uses opt-in atomic external save");
            String status=DiagnosticSupport.statusJson();ok(status.contains("\"writesEnabled\":true")&&status.contains("\"destinationName\":\"diagnostic-destination\"")&&!status.contains(root.toString()),"browser status reveals destination label but not full server path");

            Path blocked=root.resolve("blocked");Files.write(blocked,new byte[]{1});System.setProperty("sagetv.webplayer.diagnostics.dir",blocked.toString());
            DiagnosticSupport.TestResult hiddenFailure=DiagnosticSupport.testDestination();ok(!hiddenFailure.ok&&!hiddenFailure.detail.contains(root.toString())&&!hiddenFailure.detail.contains(blocked.toString()),"destination failure detail redacts configured server path");
            DiagnosticSupport.SaveResult spooled=DiagnosticSupport.save(zip);ok("spooled".equals(spooled.state)&&!spooled.detail.contains(root.toString())&&!spooled.detail.contains(blocked.toString()),"failed configured destination falls back to bounded local spool without path disclosure");
            Files.delete(blocked);Files.createDirectories(blocked);DiagnosticSupport.SaveResult cancelledRetry=DiagnosticSupport.retrySpool(new AtomicBoolean(true));ok("cancelled".equals(cancelledRetry.state)&&cancelledRetry.detail.contains("0"),"spool retry supports cooperative cancellation between bounded file steps");
            DiagnosticSupport.SaveResult retried=DiagnosticSupport.retrySpool();ok("retried".equals(retried.state)&&retried.detail.contains("Moved 1"),"spooled report retries to recovered destination");
        }finally{
            if(oldEnabled==null)System.clearProperty("sagetv.webplayer.diagnostics.writeEnabled");else System.setProperty("sagetv.webplayer.diagnostics.writeEnabled",oldEnabled);
            if(oldDir==null)System.clearProperty("sagetv.webplayer.diagnostics.dir");else System.setProperty("sagetv.webplayer.diagnostics.dir",oldDir);
            if(oldTmp==null)System.clearProperty("java.io.tmpdir");else System.setProperty("java.io.tmpdir",oldTmp);
            deleteTree(root);
        }
        System.out.println("P14 diagnostic support: "+tests+" tests PASS");
    }
}
