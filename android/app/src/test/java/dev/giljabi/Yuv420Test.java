package dev.giljabi;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class Yuv420Test {
    private static ByteBuffer bytes(int... values){byte[] b=new byte[values.length];for(int i=0;i<b.length;i++)b[i]=(byte)values[i];return ByteBuffer.wrap(b);}
    @Test public void blackAndWhiteUseLimitedRangeLuma(){
        int[] out=new int[4];Yuv420.convert(bytes(16,235,16,235),2,1,bytes(128),1,1,bytes(128),1,1,0,0,2,2,out);
        assertArrayEquals(new int[]{0xff000000,0xffffffff,0xff000000,0xffffffff},out);
    }
    @Test public void readsCropPaddingInterleavingAndPlanePositions(){
        ByteBuffer y=bytes(99,16,16,235,235,0,0,16,16,235,235,0,0);y.position(1);
        ByteBuffer u=bytes(99,128,0,90,0);u.position(1);
        ByteBuffer v=bytes(99,128,0,240);v.position(1);
        int[] out=new int[4];Yuv420.convert(y,6,1,u,4,2,v,4,2,2,0,2,2,out);
        for(int pixel:out){assertEquals(255,(pixel>>16)&255);assertTrue(((pixel>>8)&255)<200);assertTrue((pixel&255)<200);}
        assertEquals(1,y.position());assertEquals(1,u.position());assertEquals(1,v.position());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsTruncatedChroma(){Yuv420.convert(bytes(16,16,16,16),2,1,bytes(),1,1,bytes(128),1,1,0,0,2,2,new int[4]);}
    @Test(expected=IllegalArgumentException.class) public void rejectsSmallDestination(){Yuv420.convert(bytes(16,16,16,16),2,1,bytes(128),1,1,bytes(128),1,1,0,0,2,2,new int[3]);}
    @Test public void orientationTracksBothLenses(){
        for(int d=0;d<360;d+=90){assertEquals((90-d+360)%360,CameraSource.rotation(90,d,false));assertEquals((270+d)%360,CameraSource.rotation(270,d,true));}
    }
}
