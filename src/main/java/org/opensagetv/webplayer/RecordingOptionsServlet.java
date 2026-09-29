package org.opensagetv.webplayer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Manual recording padding/quality controls; mutations require an existing manual recording. */
public class RecordingOptionsServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{prep(res);try{SageApiBridge s=SageApiBridge.create();int id=HttpUtil.requiredInt(req.getParameter("airingId"),"airingId");Object a=s.getAiringForId(id);if(a==null){HttpUtil.jsonError(res,404,"Airing not found: "+id);return;}write(res,s,a);}catch(IllegalArgumentException e){HttpUtil.jsonError(res,400,e.getMessage());}catch(RuntimeException e){HttpUtil.jsonError(res,500,e.getMessage());}}
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws IOException{prep(res);try{SageApiBridge s=SageApiBridge.create();int id=HttpUtil.requiredInt(req.getParameter("airingId"),"airingId");Object a=s.getAiringForId(id);if(a==null){HttpUtil.jsonError(res,404,"Airing not found: "+id);return;}if(!s.isManualRecord(a)){HttpUtil.jsonError(res,409,"Recording options are only changed for an existing manual recording; record the airing first");return;}
        long startPad=bounded(req.getParameter("startPaddingSeconds"),0,21600,"startPaddingSeconds"),stopPad=bounded(req.getParameter("stopPaddingSeconds"),0,21600,"stopPaddingSeconds");
        long start=s.getAiringStartTime(a)-startPad*1000L,end=s.getAiringEndTime(a)+stopPad*1000L;s.setRecordingTimes(a,start,end);String q=str(req.getParameter("quality"));if(!q.isEmpty())s.setRecordingQuality(a,q);write(res,s,a);
    }catch(IllegalArgumentException e){HttpUtil.jsonError(res,400,e.getMessage());}catch(RuntimeException e){HttpUtil.jsonError(res,500,e.getMessage());}}
    private static void write(HttpServletResponse res,SageApiBridge s,Object a)throws IOException{long baseS=s.getAiringStartTime(a),baseE=s.getAiringEndTime(a),schedS=s.getScheduleStartTime(a),schedE=s.getScheduleEndTime(a);StringBuilder out=new StringBuilder();out.append("{\"airingId\":").append(s.getAiringId(a)).append(",\"manualRecord\":").append(s.isManualRecord(a)).append(",\"baseStart\":").append(baseS).append(",\"baseEnd\":").append(baseE).append(",\"scheduleStart\":").append(schedS).append(",\"scheduleEnd\":").append(schedE).append(",\"startPaddingSeconds\":").append(Math.max(0,(baseS-schedS)/1000L)).append(",\"stopPaddingSeconds\":").append(Math.max(0,(schedE-baseE)/1000L)).append(",\"quality\":\"").append(HttpUtil.json(s.getRecordingQuality(a))).append("\",\"qualities\":[");String[] qs=s.getRecordingQualities();for(int i=0;i<qs.length;i++){if(i>0)out.append(',');out.append('"').append(HttpUtil.json(qs[i])).append('"');}out.append("]}");res.getWriter().write(out.toString());}
    private static long bounded(String v,long min,long max,String n){if(v==null||v.trim().isEmpty())return 0;try{long x=Long.parseLong(v);if(x<min||x>max)throw new IllegalArgumentException(n+" must be "+min+".."+max);return x;}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid number for "+n+": "+v);}}
    private static String str(String s){return s==null?"":s.trim();}
    private static void prep(HttpServletResponse r){r.setCharacterEncoding("UTF-8");r.setContentType("application/json");r.setHeader("Cache-Control","no-store");}
}
