package dev.giljabi;
import org.junit.Test;
import static org.junit.Assert.*;
public class ContinuousGateTest {
 @Test public void singleFlightAndNewFrames(){ContinuousGate g=new ContinuousGate();g.start();ContinuousGate.Ticket a=g.begin(1,0,0,3000);assertNotNull(a);assertNull(g.begin(2,3000,3000,3000));assertTrue(g.finish(a));assertNull(g.begin(1,3000,3000,3000));assertNotNull(g.begin(3,3000,3000,3000));}
 @Test public void skipsStaleFutureAndEarlyFrames(){ContinuousGate g=new ContinuousGate();g.start();assertNull(g.begin(1,0,5001,1000));assertNull(g.begin(1,6000,5000,1000));ContinuousGate.Ticket a=g.begin(2,5000,5000,3000);assertNotNull(a);g.finish(a);assertNull(g.begin(3,6000,6000,3000));assertNotNull(g.begin(4,8000,8000,3000));}
 @Test public void switchWaitsForOldWorkAndRejectsItsResult(){ContinuousGate g=new ContinuousGate();g.start();ContinuousGate.Ticket a=g.begin(1,0,0,3000);g.reset();assertFalse(g.current(a));assertNull(g.begin(2,1000,1000,3000));assertFalse(g.finish(a));ContinuousGate.Ticket b=g.begin(2,1000,1000,3000);assertNotNull(b);g.finish(a);assertNull(g.begin(3,5000,5000,3000));assertTrue(g.current(b));}
 @Test public void cameraReconnectPreservesPause(){ContinuousGate g=new ContinuousGate();g.start();ContinuousGate.Ticket a=g.begin(1,0,0,1000);g.pause(a);g.finish(a);g.invalidateFrames();assertTrue(g.isPaused());assertNull(g.begin(2,5000,5000,1000));g.reset();assertFalse(g.isPaused());assertNotNull(g.begin(2,5000,5000,1000));}
 @Test public void stopSuppressesLateResultsAndNewStartWaits(){ContinuousGate g=new ContinuousGate();g.start();ContinuousGate.Ticket a=g.begin(1,0,0,1000);g.stop();assertFalse(g.current(a));g.start();assertNull(g.begin(2,1000,1000,1000));assertFalse(g.finish(a));assertNotNull(g.begin(2,1000,1000,1000));}
 @Test public void transientFailuresBackOffAndOldErrorsCannotPauseNewMode(){ContinuousGate g=new ContinuousGate();g.start();ContinuousGate.Ticket a=g.begin(1,0,0,1000);g.defer(a,10000);g.finish(a);assertNull(g.begin(2,5000,5000,1000));g.reset();g.pause(a);g.defer(a,100000);assertFalse(g.isPaused());assertNotNull(g.begin(3,5000,5000,1000));}
 @Test public void minimumIntervalIsEnforced(){ContinuousGate g=new ContinuousGate();g.start();ContinuousGate.Ticket a=g.begin(1,0,0,0);g.finish(a);assertNull(g.begin(2,500,500,0));assertNotNull(g.begin(3,1000,1000,0));}
}
