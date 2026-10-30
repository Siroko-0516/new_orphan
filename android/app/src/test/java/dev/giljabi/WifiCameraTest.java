package dev.giljabi;

import org.junit.Test;
import static org.junit.Assert.*;

public class WifiCameraTest {
    private byte[] ip(int a,int b,int c,int d){return new byte[]{(byte)a,(byte)b,(byte)c,(byte)d};}
    @Test public void distinguishesEspApFromUnrelatedHomeWifi(){
        assertTrue(WifiCamera.sameSubnet(ip(192,168,4,1),ip(192,168,4,2),24));
        assertFalse(WifiCamera.sameSubnet(ip(192,168,4,1),ip(192,168,0,2),24));
    }
    @Test public void handlesNonOctetAlignedAndHostRoutes(){
        assertTrue(WifiCamera.sameSubnet(ip(172,17,8,1),ip(172,16,1,2),15));
        assertFalse(WifiCamera.sameSubnet(ip(172,18,8,1),ip(172,16,1,2),15));
        assertTrue(WifiCamera.sameSubnet(ip(10,0,0,1),ip(10,0,0,1),32));
        assertFalse(WifiCamera.sameSubnet(ip(10,0,0,1),ip(10,0,0,2),32));
    }
    @Test public void defaultRouteDoesNotProveCameraIsOnThisWifi(){
        assertFalse(WifiCamera.sameSubnet(ip(192,168,4,1),ip(10,0,0,2),0));
        assertFalse(WifiCamera.sameSubnet(ip(192,168,4,1),new byte[16],24));
    }
}
