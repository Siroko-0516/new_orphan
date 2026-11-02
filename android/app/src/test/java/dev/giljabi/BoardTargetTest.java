package dev.giljabi;
import org.junit.Test;import static org.junit.Assert.*;import java.io.IOException;
public class BoardTargetTest {
 @Test public void endpointComparisonIgnoresPresentationCase()throws Exception{assertEquals(new BoardTarget("aa:bb:cc:dd:ee:ff",BleLink.DEFAULT_SERVICE.toUpperCase(),BleLink.DEFAULT_CHARACTERISTIC),new BoardTarget("AA:BB:CC:DD:EE:FF",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC));}
 @Test public void differentDeviceIsDifferentTarget()throws Exception{assertNotEquals(new BoardTarget("AA:BB:CC:DD:EE:01",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC),new BoardTarget("AA:BB:CC:DD:EE:02",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC));}
 @Test public void invalidEndpointsAreRejectedBeforeConnection(){assertThrows(IOException.class,()->new BoardTarget("",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC));assertThrows(IOException.class,()->new BoardTarget("AA:BB:CC:DD:EE:01","not-a-uuid",BleLink.DEFAULT_CHARACTERISTIC));}
}
