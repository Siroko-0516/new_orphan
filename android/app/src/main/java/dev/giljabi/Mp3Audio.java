package dev.giljabi;

/** Sniffs an MP3/ID3 header before MediaPlayer performs the complete audio decode. */
final class Mp3Audio {
    static boolean hasHeader(byte[] audio){
        if(audio==null||audio.length<4)return false;int start=0;
        if(audio[0]=='I'&&audio[1]=='D'&&audio[2]=='3'){
            if(audio.length<10)return false;
            int version=audio[3]&255;if(version<2||version>4)return false;
            int size=0;for(int i=6;i<10;i++){int n=audio[i]&255;if((n&128)!=0)return false;size=(size<<7)|n;}
            long end=10L+size+(version==4&&(audio[5]&16)!=0?10:0);
            if(end>audio.length-4)return false;start=(int)end;
        }
        int a=audio[start]&255,b=audio[start+1]&255,c=audio[start+2]&255;
        return a==255&&(b&224)==224&&((b>>3)&3)!=1&&((b>>1)&3)!=0&&(c>>4)!=0&&(c>>4)!=15&&((c>>2)&3)!=3;
    }
}
