package dev.giljabi;

import android.graphics.Bitmap;

final class ImageEvidence {
    static SceneSignature sample(Bitmap bitmap){
        int[] rgb=new int[SceneSignature.WIDTH*SceneSignature.HEIGHT];
        for(int y=0;y<SceneSignature.HEIGHT;y++)for(int x=0;x<SceneSignature.WIDTH;x++)rgb[y*SceneSignature.WIDTH+x]=bitmap.getPixel(Math.min(bitmap.getWidth()-1,(2*x+1)*bitmap.getWidth()/(2*SceneSignature.WIDTH)),Math.min(bitmap.getHeight()-1,(2*y+1)*bitmap.getHeight()/(2*SceneSignature.HEIGHT)));
        return new SceneSignature(rgb);
    }
}
