package org.opensagetv.webplayer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Maps decoded Teletext subtitle text onto SageTV's legacy CC1/CC2 callback. */
final class TeletextCea608Bridge {
    private TeletextCea608Bridge(){}
    static byte[] encode(String text,int channel,long ptsMs){
        int pts=(int)Math.min(0xffffffffL,Math.max(0L,ptsMs)*45L);ByteArrayOutputStream out=new ByteArrayOutputStream();
        writeControl(out,pts,channel,0x2c);writeControl(out,pts,channel,0x2e);writeControl(out,pts,channel,0x20);
        List<String> lines=formatLines(text);int row=12+Math.max(0,3-lines.size());for(String line:lines){writePac(out,pts,channel,Math.min(14,row));writeText(out,pts,line);row++;}writeControl(out,pts,channel,0x2f);return out.toByteArray();
    }
    static List<String> formatLines(String text){ArrayList<String> lines=new ArrayList<String>();String n=text==null?"":text.replace('\n',' ').trim().replaceAll("\\s+"," ");StringBuilder line=new StringBuilder();for(String word:n.split(" ")){if(word.length()==0)continue;while(word.length()>32){if(line.length()>0){lines.add(line.toString());line.setLength(0);}lines.add(word.substring(0,32));word=word.substring(32);}if(line.length()>0&&line.length()+1+word.length()>32){lines.add(line.toString());line.setLength(0);}if(line.length()>0)line.append(' ');line.append(word);}if(line.length()>0)lines.add(line.toString());if(lines.size()<=3)return lines;return new ArrayList<String>(lines.subList(lines.size()-3,lines.size()));}
    private static void writeControl(ByteArrayOutputStream out,int pts,int channel,int second){int first=0x14|(channel==1?8:0);writeRecord(out,pts,first,second);writeRecord(out,pts,first,second);}
    private static void writePac(ByteArrayOutputStream out,int pts,int channel,int row){final int[] rf={0,0x11,0x11,0x12,0x12,0x15,0x15,0x16,0x16,0x17,0x17,0x10,0x13,0x13,0x14,0x14};boolean lower=row==2||row==4||row==6||row==8||row==10||row==13||row==15;writeRecord(out,pts,rf[row]|(channel==1?8:0),0x40|(lower?0x20:0)|(8<<1));}
    private static void writeText(ByteArrayOutputStream out,int pts,String text){String s=text==null?"":text;for(int i=0;i<s.length();i+=2){int a=character(s.charAt(i)),b=i+1<s.length()?character(s.charAt(i+1)):0;writeRecord(out,pts,a,b);}}
    private static int character(char c){return c>=0x20&&c<=0x7f?c:0x20;}
    private static void writeRecord(ByteArrayOutputStream out,int pts,int a,int b){out.write((pts>>>24)&255);out.write((pts>>>16)&255);out.write((pts>>>8)&255);out.write(pts&255);out.write(0);out.write(parity(a));out.write(parity(b));out.write(1);}
    private static int parity(int v){int x=v&0x7f;return (Integer.bitCount(x)&1)==0?x|0x80:x;}
}
