package org.opensagetv.webplayer;

import java.io.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Tail one immutable stream-session file. A reconnect resumes bytes, never the encoder. */
final class ContinuousFilePump {
    static void copy(File file,long offset,OutputStream out,BooleanSupplier producing,
                     BooleanSupplier cancelled,LongConsumer delivered) throws IOException,InterruptedException {
        if(offset<0)throw new IllegalArgumentException("Negative stream offset");
        long deadline=System.currentTimeMillis()+30000L;
        while(!file.isFile()&&!cancelled.getAsBoolean()) {
            if(!producing.getAsBoolean()||System.currentTimeMillis()>deadline)throw new IOException("No continuous stream output");
            Thread.sleep(40L);
        }
        if(cancelled.getAsBoolean())return;
        try(RandomAccessFile in=new RandomAccessFile(file,"r")) {
            if(offset>in.length())throw new IOException("Offset exceeds produced stream bytes");
            in.seek(offset);byte[] b=new byte[32768];
            while(!cancelled.getAsBoolean()&&!Thread.currentThread().isInterrupted()) {
                if(in.length()<in.getFilePointer())throw new IOException("Continuous stream output shrank or was replaced");
                int n=in.read(b);
                if(n>0) {out.write(b,0,n);out.flush();delivered.accept(n);continue;}
                if(!producing.getAsBoolean()) {
                    // A writer may have completed between the read and the liveness check.
                    if(in.getFilePointer()<in.length())continue;
                    break;
                }
                Thread.sleep(40L);
            }
        }
    }
}
