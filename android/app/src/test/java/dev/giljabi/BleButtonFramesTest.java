package dev.giljabi;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;import java.nio.charset.StandardCharsets;
public class BleButtonFramesTest {
 @Test public void fragmentedButtonAndCrLf(){BleButtonFrames f=new BleButtonFrames();List<String> result=new ArrayList<>();f.accept("AS".getBytes(StandardCharsets.US_ASCII),result::add);f.accept("SIST\r\n".getBytes(StandardCharsets.US_ASCII),result::add);assertEquals(Collections.singletonList("ASSIST"),result);}
 @Test public void ignoresUnknownAndOverlongLines(){BleButtonFrames f=new BleButtonFrames();List<String> result=new ArrayList<>();f.accept(("TEST\n"+String.join("",Collections.nCopies(40,"A"))+"ASSIST\nASSIST\n").getBytes(StandardCharsets.US_ASCII),result::add);assertEquals(Collections.singletonList("ASSIST"),result);}
 @Test public void resetCannotCombineDifferentConnections(){BleButtonFrames f=new BleButtonFrames();List<String> result=new ArrayList<>();f.accept("AS".getBytes(StandardCharsets.US_ASCII),result::add);f.reset();f.accept("SIST\n".getBytes(StandardCharsets.US_ASCII),result::add);assertTrue(result.isEmpty());}
}
