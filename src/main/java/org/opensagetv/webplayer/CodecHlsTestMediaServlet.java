package org.opensagetv.webplayer;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Serves only the fixed synthetic HLS artifacts created by CodecHlsTestManager. */
public class CodecHlsTestMediaServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        final File f;
        try{f=CodecHlsTestManager.mediaFile(req.getPathInfo());}
        catch(IOException e){resp.sendError(404);return;}
        if(!f.isFile()||f.length()<=0){resp.sendError(404);return;}
        String name=f.getName();
        resp.setContentType(name.endsWith(".m3u8")?"application/vnd.apple.mpegurl":"video/mp2t");
        resp.setHeader("Cache-Control","no-store, no-cache, must-revalidate, max-age=0");
        resp.setHeader("Accept-Ranges","none");resp.setHeader("X-Content-Type-Options","nosniff");
        byte[] data=Files.readAllBytes(f.toPath());resp.setContentLength(data.length);
        ServletOutputStream out=resp.getOutputStream();out.write(data);out.flush();
    }
}
