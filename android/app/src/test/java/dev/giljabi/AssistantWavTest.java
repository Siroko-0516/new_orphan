package dev.giljabi;
import org.junit.Test;import static org.junit.Assert.*;import java.nio.*;
public class AssistantWavTest {
 @Test public void pcmIsWrappedWithoutAlteration(){byte[] pcm=new byte[32000];pcm[0]=12;byte[] wav=AssistantWav.encode(pcm);assertEquals(32044,wav.length);assertEquals('R',wav[0]);ByteBuffer b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);assertEquals(16000,b.getInt(24));assertEquals(32000,b.getInt(40));assertEquals(12,wav[44]);}
 @Test(expected=IllegalArgumentException.class)public void recordingSizeIsBounded(){AssistantWav.encode(new byte[500000]);}
}
