package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Read-only tuner/encoder health plus narrowly-scoped quality/merit controls. */
public class TunersServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{
        prep(res);try{
            SageApiBridge s=SageApiBridge.create();Set<String> active=new HashSet<String>(Arrays.asList(s.getActiveCaptureDevices()));String[] devs=s.getCaptureDevices();
            StringBuilder out=new StringBuilder(16384);out.append("{\"defaultQuality\":\"").append(HttpUtil.json(s.getDefaultRecordingQuality())).append("\",\"qualities\":[");
            String[] qualities=s.getRecordingQualities();for(int i=0;i<qualities.length;i++){if(i>0)out.append(',');String q=qualities[i];out.append("{\"name\":\"").append(HttpUtil.json(q)).append("\",\"bitrate\":").append(s.getRecordingQualityBitrate(q)).append(",\"format\":\"").append(HttpUtil.json(s.getRecordingQualityFormat(q))).append("\"}");}
            out.append("],\"devices\":[");
            for(int i=0;i<devs.length;i++){if(i>0)out.append(',');String d=devs[i];Object current=s.getCaptureDeviceCurrentRecordFile(d);int currentId=current==null?0:s.getMediaFileId(current);out.append('{')
                .append("\"name\":\"").append(HttpUtil.json(d)).append("\",")
                .append("\"functioning\":").append(s.isCaptureDeviceFunctioning(d)).append(',')
                .append("\"networkEncoder\":").append(s.isCaptureDeviceNetworkEncoder(d)).append(',')
                .append("\"active\":").append(active.contains(d)).append(',')
                .append("\"liveClient\":").append(s.isCaptureDeviceInUseByLiveClient(d)).append(',')
                .append("\"currentMediaId\":").append(currentId).append(',')
                .append("\"broadcastStandard\":\"").append(HttpUtil.json(s.getCaptureDeviceBroadcastStandard(d))).append("\",")
                .append("\"defaultQuality\":\"").append(HttpUtil.json(s.getCaptureDeviceDefaultQuality(d))).append("\",")
                .append("\"merit\":").append(s.getCaptureDeviceMerit(d)).append(',').append("\"qualities\":[");
                String[] dq=s.getCaptureDeviceQualities(d);for(int j=0;j<dq.length;j++){if(j>0)out.append(',');out.append('"').append(HttpUtil.json(dq[j])).append('"');}
                out.append("],\"inputs\":[");String[] inputs=s.getCaptureDeviceInputs(d);for(int j=0;j<inputs.length;j++){if(j>0)out.append(',');String in=inputs[j];out.append("{\"name\":\"").append(HttpUtil.json(in)).append("\",\"lineup\":\"").append(HttpUtil.json(safeLineup(s,in))).append("\",\"signal\":").append(safeSignal(s,in)).append(",\"standard\":\"").append(HttpUtil.json(safeStandard(s,in))).append("\"}");}
                out.append("]}");
            }
            out.append("]}");res.getWriter().write(out.toString());
        }catch(RuntimeException e){HttpUtil.jsonError(res,500,e.getMessage());}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws IOException{prep(res);try{SageApiBridge s=SageApiBridge.create();String action=str(req.getParameter("action"));
        if("globalQuality".equalsIgnoreCase(action)){String q=str(req.getParameter("quality"));s.setDefaultRecordingQuality(q);res.getWriter().write("{\"ok\":true}");return;}
        String device=str(req.getParameter("device"));if(device.isEmpty()){HttpUtil.jsonError(res,400,"device is required");return;}
        if("deviceQuality".equalsIgnoreCase(action)){s.setCaptureDeviceDefaultQuality(device,str(req.getParameter("quality")));res.getWriter().write("{\"ok\":true}");return;}
        if("merit".equalsIgnoreCase(action)){int merit=HttpUtil.requiredInt(req.getParameter("merit"),"merit");if(merit<0||merit>100){HttpUtil.jsonError(res,400,"merit must be 0..100");return;}s.setCaptureDeviceMerit(device,merit);res.getWriter().write("{\"ok\":true}");return;}
        HttpUtil.jsonError(res,400,"Unsupported tuner action: "+action);
    }catch(IllegalArgumentException e){HttpUtil.jsonError(res,400,e.getMessage());}catch(RuntimeException e){HttpUtil.jsonError(res,500,e.getMessage());}}
    private static String safeLineup(SageApiBridge s,String in){try{return s.getLineupForCaptureDeviceInput(in);}catch(RuntimeException e){return "";}}
    private static String safeStandard(SageApiBridge s,String in){try{return s.getCaptureDeviceInputBroadcastStandard(in);}catch(RuntimeException e){return "";}}
    private static int safeSignal(SageApiBridge s,String in){try{return s.getSignalStrength(in);}catch(RuntimeException e){return -1;}}
    private static void prep(HttpServletResponse r){r.setCharacterEncoding("UTF-8");r.setContentType("application/json");r.setHeader("Cache-Control","no-store");}
    private static String str(String s){return s==null?"":s.trim();}
}
