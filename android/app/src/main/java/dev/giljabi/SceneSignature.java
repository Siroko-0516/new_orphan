package dev.giljabi;

/** A small spatial colour sample. Global exposure changes are subtracted before comparison. */
final class SceneSignature {
    static final int WIDTH=16,HEIGHT=12;
    private final int[] rgb;
    private final double brightness;
    SceneSignature(int[] pixels){
        if(pixels.length!=WIDTH*HEIGHT)throw new IllegalArgumentException("Invalid scene sample");
        rgb=pixels.clone();double sum=0;
        for(int p:rgb)sum+=(77*((p>>16)&255)+150*((p>>8)&255)+29*(p&255))/256.0;
        brightness=sum/rgb.length;
    }
    String unclearMessage(){
        if(brightness<10)return "영상이 너무 어두워 주변 물체를 확인하기 어렵습니다.";
        if(brightness>245)return "영상이 너무 밝아 주변 물체를 확인하기 어렵습니다.";
        double[] mean=new double[3];
        for(int p:rgb){mean[0]+=(p>>16)&255;mean[1]+=(p>>8)&255;mean[2]+=p&255;}
        for(int i=0;i<3;i++)mean[i]/=rgb.length;
        double variance=0;
        for(int p:rgb){variance+=Math.pow(((p>>16)&255)-mean[0],2)+Math.pow(((p>>8)&255)-mean[1],2)+Math.pow((p&255)-mean[2],2);}
        return variance/(rgb.length*3)<100?"영상이 단색에 가까워 주변 물체를 확인하기 어렵습니다.":null;
    }
    double difference(SceneSignature other){
        double exposure=brightness-other.brightness,total=0;
        for(int i=0;i<rgb.length;i++)total+=pixelDifference(rgb[i],other.rgb[i],exposure);
        return total/rgb.length/255;
    }
    boolean majorChange(SceneSignature other){
        double exposure=brightness-other.brightness,total=0;int changed=0;
        for(int i=0;i<rgb.length;i++){double d=pixelDifference(rgb[i],other.rgb[i],exposure);total+=d;if(d>33)changed++;}
        return total/rgb.length>=38&&changed>=rgb.length*.40;
    }
    private static double pixelDifference(int a,int b,double exposure){
        return (Math.abs(((a>>16)&255)-((b>>16)&255)-exposure)+Math.abs(((a>>8)&255)-((b>>8)&255)-exposure)+Math.abs((a&255)-(b&255)-exposure))/3;
    }
}
