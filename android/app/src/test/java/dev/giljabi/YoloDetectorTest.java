package dev.giljabi;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class YoloDetectorTest {
    @Test public void restoresLetterboxCoordinates(){
        float[][] output=new float[84][1];output[0][0]=208;output[1][0]=208;output[2][0]=208;output[3][0]=104;output[4][0]=.9f;
        List<YoloDetector.Box> boxes=YoloDetector.decode(output,832,416);
        assertEquals(1,boxes.size());assertArrayEquals(new float[]{.25f,.25f,.75f,.75f},boxes.get(0).xy,.001f);
    }
    @Test public void removesSameClassDuplicatesButKeepsOtherClasses(){
        float[][] output=new float[84][3];for(int i=0;i<3;i++){output[0][i]=208;output[1][i]=208;output[2][i]=100;output[3][i]=100;}
        output[4][0]=.9f;output[4][1]=.8f;output[9][2]=.85f;
        List<YoloDetector.Box> boxes=YoloDetector.decode(output,416,416);
        assertEquals(2,boxes.size());assertEquals(0,boxes.get(0).label);assertEquals(5,boxes.get(1).label);
    }
}
