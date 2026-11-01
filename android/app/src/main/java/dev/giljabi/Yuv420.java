package dev.giljabi;

import java.nio.ByteBuffer;

/** Reads each plane from its current position, including padded/interleaved camera buffers. */
final class Yuv420 {
    static void convert(ByteBuffer y,int yr,int yp,ByteBuffer u,int ur,int up,ByteBuffer v,int vr,int vp,
                        int left,int top,int width,int height,int[] pixels){
        if(left<0||top<0||width<=0||height<=0||(long)width*height>pixels.length||yr<=0||yp<=0||ur<=0||up<=0||vr<=0||vp<=0)
            throw new IllegalArgumentException("Invalid camera image layout");
        for(int row=0;row<height;row++)for(int col=0;col<width;col++){
            int x=left+col,z=top+row;
            int yy=sample(y,yr,yp,x,z)-16,uu=sample(u,ur,up,x/2,z/2)-128,vv=sample(v,vr,vp,x/2,z/2)-128;
            int c=298*Math.max(0,yy);
            pixels[row*width+col]=0xff000000|(clamp((c+409*vv+128)>>8)<<16)|(clamp((c-100*uu-208*vv+128)>>8)<<8)|clamp((c+516*uu+128)>>8);
        }
    }
    private static int sample(ByteBuffer b,int row,int pixel,int x,int y){
        long at=(long)b.position()+(long)y*row+(long)x*pixel;
        if(at<b.position()||at>=b.limit())throw new IllegalArgumentException("Truncated camera image plane");
        return b.get((int)at)&255;
    }
    private static int clamp(int n){return Math.max(0,Math.min(255,n));}
}
