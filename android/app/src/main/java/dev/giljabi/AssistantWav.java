package dev.giljabi;

import java.io.*;

final class AssistantWav {
    static final int SAMPLE_RATE=16000,MAX_SECONDS=60,MAX_PCM=SAMPLE_RATE*2*MAX_SECONDS,MAX_WAV=MAX_PCM+44;
    static byte[] encode(byte[] pcm){
        if(pcm==null||pcm.length<320||pcm.length>MAX_PCM||(pcm.length&1)!=0)throw new IllegalArgumentException("Invalid bounded PCM");
        ByteArrayOutputStream out=new ByteArrayOutputStream(pcm.length+44);
        write(out,"RIFF");le(out,pcm.length+36,4);write(out,"WAVEfmt ");le(out,16,4);le(out,1,2);le(out,1,2);le(out,16000,4);le(out,32000,4);le(out,2,2);le(out,16,2);write(out,"data");le(out,pcm.length,4);out.write(pcm,0,pcm.length);return out.toByteArray();
    }
    private static void write(ByteArrayOutputStream out,String value){for(int i=0;i<value.length();i++)out.write(value.charAt(i));}
    private static void le(ByteArrayOutputStream out,int value,int bytes){for(int i=0;i<bytes;i++)out.write((value>>>(i*8))&255);}
}
