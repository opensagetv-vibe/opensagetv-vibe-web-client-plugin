package org.opensagetv.webplayer;

import java.util.Locale;

/** Physical DVD audio selector sent by stock MiniDVDPlayer command 36/type 0. */
final class DvdAudioStreamCode {
    enum Family { MPEG_AUDIO, AC3, DTS, SDDS, LPCM, PRIVATE_UNKNOWN, UNKNOWN }
    final int rawCode;
    final int pesStreamId;
    final int privateSubstreamId;
    final Family family;
    final int familyOrdinal;

    private DvdAudioStreamCode(int raw,int pes,int sub,Family family,int ordinal){
        this.rawCode=raw;this.pesStreamId=pes;this.privateSubstreamId=sub;this.family=family;this.familyOrdinal=ordinal;
    }

    static DvdAudioStreamCode decode(int wireCode){
        int raw=wireCode&0xffff,pes=(raw>>>8)&0xff,sub=raw&0xff;
        if(pes>=0xc0&&pes<=0xdf)return new DvdAudioStreamCode(raw,pes,-1,Family.MPEG_AUDIO,pes-0xc0);
        if(pes!=0xbd)return new DvdAudioStreamCode(raw,pes,sub,Family.UNKNOWN,-1);
        if(sub>=0x80&&sub<=0x87)return new DvdAudioStreamCode(raw,pes,sub,Family.AC3,sub-0x80);
        if(sub>=0x88&&sub<=0x8f)return new DvdAudioStreamCode(raw,pes,sub,Family.DTS,sub-0x88);
        if(sub>=0x90&&sub<=0x97)return new DvdAudioStreamCode(raw,pes,sub,Family.SDDS,sub-0x90);
        if(sub>=0xa0&&sub<=0xa7)return new DvdAudioStreamCode(raw,pes,sub,Family.LPCM,sub-0xa0);
        return new DvdAudioStreamCode(raw,pes,sub,Family.PRIVATE_UNKNOWN,-1);
    }

    boolean supportedByFfmpegPolicy(){
        return family==Family.MPEG_AUDIO||family==Family.AC3||family==Family.DTS||family==Family.LPCM;
    }

    /** FFmpeg's MPEG-PS demuxer exposes private substream ids as 0x80.. and MPEG audio as 0x1c0... */
    String ffmpegStreamId(){
        if(family==Family.MPEG_AUDIO)return String.format(Locale.US,"0x1%02x",pesStreamId);
        if(pesStreamId==0xbd&&privateSubstreamId>=0)return String.format(Locale.US,"0x%02x",privateSubstreamId);
        return "";
    }

    String description(){
        return family+" wire=0x"+Integer.toHexString(rawCode)+" ffmpegId="+ffmpegStreamId();
    }
}
