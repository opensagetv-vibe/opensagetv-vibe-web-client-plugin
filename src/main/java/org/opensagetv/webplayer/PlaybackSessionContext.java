package org.opensagetv.webplayer;

import java.util.UUID;

/**
 * One browser MiniClient media lifetime. Source, seek and FLUSH generations are
 * independent so asynchronous work can reject callbacks from a retired epoch.
 */
final class PlaybackSessionContext {
    static final class Token {
        final String sessionId;
        final long sourceGeneration, seekGeneration, flushGeneration;
        Token(String id,long source,long seek,long flush){sessionId=id;sourceGeneration=source;seekGeneration=seek;flushGeneration=flush;}
        String json(){return "{\"sessionId\":\""+HttpUtil.json(sessionId)+"\",\"sourceGeneration\":"+sourceGeneration+
                ",\"seekGeneration\":"+seekGeneration+",\"flushGeneration\":"+flushGeneration+"}";}
    }

    private final String sessionId=UUID.randomUUID().toString().replace("-","");
    private long sourceGeneration,seekGeneration,flushGeneration;
    private boolean sourceActive;
    private int selectedAudio=-1,selectedSubtitle=-1;
    private String selectedCaption="off";
    private final SubtitleCueQueue cues=new SubtitleCueQueue(this);
    private final SubtitleCueQueue fileCues=new SubtitleCueQueue(this);

    Token beginSource(){Token t; synchronized(this){sourceGeneration++;seekGeneration=0;flushGeneration=0;sourceActive=true;selectedAudio=-1;selectedSubtitle=-1;selectedCaption="off";t=token();}cues.clearAll();fileCues.clearAll();return t;}
    Token beginSeek(){Token t; synchronized(this){if(sourceActive)seekGeneration++;t=token();}cues.clearAll();fileCues.clearAll();return t;}
    Token beginFlush(){Token t; synchronized(this){if(sourceActive)flushGeneration++;t=token();}cues.clearAll();fileCues.clearAll();return t;}
    Token invalidateSource(){Token t; synchronized(this){if(sourceActive){sourceGeneration++;seekGeneration=0;flushGeneration=0;}sourceActive=false;t=token();}cues.clearAll();fileCues.clearAll();return t;}
    synchronized Token token(){return new Token(sessionId,sourceGeneration,seekGeneration,flushGeneration);}
    synchronized boolean isCurrent(Token t){return t!=null&&sourceActive&&sessionId.equals(t.sessionId)&&sourceGeneration==t.sourceGeneration&&seekGeneration==t.seekGeneration&&flushGeneration==t.flushGeneration;}
    synchronized boolean sourceActive(){return sourceActive;}
    synchronized void selectAudio(int index){selectedAudio=index;}
    synchronized void selectSubtitle(int index){selectedSubtitle=index;}
    synchronized void selectCaption(String caption){selectedCaption=caption==null?"off":caption;}
    synchronized String selectionJson(){return "{\"audioTrack\":"+selectedAudio+",\"subtitleTrack\":"+selectedSubtitle+",\"caption\":\""+HttpUtil.json(selectedCaption)+"\"}";}
    SubtitleCueQueue cues(){return cues;}
    SubtitleCueQueue fileCues(){return fileCues;}
}
