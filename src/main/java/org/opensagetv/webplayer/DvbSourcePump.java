package org.opensagetv.webplayer;

import java.io.File;

/** Reads original recording bytes for the independent DVB bitmap decoder. */
final class DvbSourcePump {
    private final HlsSessionManager.Session session; private final SageApiBridge sage; private final Object mediaFile; private final DvbSubtitleSession decoder; private final long absoluteStart;
    private volatile Thread thread; private volatile boolean stop; volatile long bytes; volatile String error="";
    private DvbSourcePump(HlsSessionManager.Session session,SageApiBridge sage,Object mediaFile,DvbSubtitleSession decoder,long absoluteStart){this.session=session;this.sage=sage;this.mediaFile=mediaFile;this.decoder=decoder;this.absoluteStart=Math.max(0L,absoluteStart);}
    static DvbSourcePump start(HlsSessionManager.Session session,SageApiBridge sage,Object mediaFile,DvbSubtitleSession decoder,long absoluteStart){DvbSourcePump p=new DvbSourcePump(session,sage,mediaFile,decoder,absoluteStart);p.start();return p;}
    private void start(){thread=new Thread(()->run(),"webplayer-dvb-"+session.id.substring(0,Math.min(8,session.id.length())));thread.setDaemon(true);thread.start();}
    private void run(){long skip=absoluteStart;int segment=session.firstSegment;byte[] buffer=new byte[64*1024];try{while(active()){int count=sage.getNumberOfSegments(mediaFile);boolean recording=sage.isFileCurrentlyRecording(mediaFile);if(segment>=count){if(!recording)break;sleep(200);continue;}File f=sage.getFileForSegment(mediaFile,segment);if(f==null){segment++;continue;}try(SeekableMediaSource src=MediaSourceFactory.open(sage,f)){long len=Math.max(0L,src.length());if(skip>=len&&len>0&&segment<count-1){skip-=len;segment++;continue;}long pos=Math.min(skip,len);if(pos>0)src.seek(pos);skip=0;while(active()){long available=Math.max(0L,src.length()-pos);if(available<=0){count=sage.getNumberOfSegments(mediaFile);recording=sage.isFileCurrentlyRecording(mediaFile);if(src.length()>pos)continue;if(segment<count-1||!recording)break;sleep(200);continue;}int n=src.read(buffer,0,(int)Math.min(buffer.length,available));if(n<=0){if(recording){sleep(200);continue;}break;}decoder.observe(f.getAbsolutePath(),pos,buffer,0,n);pos+=n;bytes+=n;}}segment++;}}catch(Exception e){if(!stop&&!session.stopRequested)error=compact(e);}finally{decoder.finish();}}
    void stop(){stop=true;Thread t=thread;if(t!=null)t.interrupt();decoder.finish();}
    String json(){return "{\"running\":"+(thread!=null&&thread.isAlive()&&!stop)+",\"bytes\":"+bytes+",\"error\":\""+HttpUtil.json(error)+"\"}";}
    private boolean active(){return !stop&&!session.stopRequested&&!Thread.currentThread().isInterrupted();}private static void sleep(long ms)throws InterruptedException{Thread.sleep(ms);}private static String compact(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s.replace('\r',' ').replace('\n',' ');}
}
