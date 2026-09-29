package org.opensagetv.webplayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Bounded presentation queue shared by text and bitmap decoders. */
final class SubtitleCueQueue {
    private final PlaybackSessionContext owner;
    private final PriorityQueue<SubtitleCue> q=new PriorityQueue<SubtitleCue>(Comparator.comparingLong(c->c.ptsMs));
    private long bytes,droppedStale,droppedLimit;
    SubtitleCueQueue(PlaybackSessionContext owner){this.owner=owner;}
    synchronized boolean offer(SubtitleCue cue){
        if(!owner.isCurrent(cue.token)){droppedStale++;return false;}
        int n=cue.bytes();if(n>SubtitleLimits.MAX_CUE_BYTES||q.size()>=SubtitleLimits.MAX_CUES||bytes+n>SubtitleLimits.MAX_QUEUED_BYTES){droppedLimit++;return false;}
        q.add(cue);bytes+=n;return true;
    }
    synchronized List<SubtitleCue> drainDue(PlaybackSessionContext.Token token,long presentationMs,int max){
        List<SubtitleCue> out=new ArrayList<SubtitleCue>();if(!owner.isCurrent(token))return out;max=Math.max(1,Math.min(max,512));
        while(!q.isEmpty()&&q.peek().ptsMs<=presentationMs&&out.size()<max){SubtitleCue c=q.poll();bytes-=c.bytes();if(owner.isCurrent(c.token))out.add(c);else droppedStale++;}
        return out;
    }
    synchronized void clearAll(){q.clear();bytes=0;}
    synchronized int size(){return q.size();}
    synchronized long bytes(){return bytes;}
    synchronized String json(){return json(-1L);}
    synchronized String json(long presentationMs){
        long oldest=q.isEmpty()?-1L:q.peek().ptsMs,newest=-1L;for(SubtitleCue c:q)if(c.ptsMs>newest)newest=c.ptsMs;
        long age=(presentationMs>=0&&oldest>=0)?presentationMs-oldest:-1L;
        return "{\"pending\":"+q.size()+",\"bytes\":"+bytes+",\"droppedStale\":"+droppedStale+",\"droppedLimit\":"+droppedLimit+",\"oldestPtsMs\":"+oldest+",\"newestPtsMs\":"+newest+",\"oldestAgeMs\":"+age+"}";
    }
}
