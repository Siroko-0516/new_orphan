package dev.giljabi;

import android.Manifest;
import android.content.Intent;
import android.os.Looper;
import android.view.*;
import android.widget.Button;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** No radio is simulated as a real device: these assert the app's owner/lifetime contract. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class BoardServiceLifecycleTest {
    static final String ADDRESS="AA:BB:CC:DD:EE:01";
    public static final class TestBoardService extends BoardService {
        FakeLink fake;
        @Override BoardTransport createTransport(Consumer<String> status,Runnable changed,Consumer<String> received){return fake=new FakeLink(status,changed,received);}
    }
    static final class FakeLink implements BoardTransport {
        final Consumer<String> status,received;final Runnable changed;boolean ready;
        int connects,closes;final List<String> sent=new ArrayList<>();
        FakeLink(Consumer<String> s,Runnable c,Consumer<String> r){status=s;changed=c;received=r;}
        public boolean isReady(){return ready;}public boolean isConnecting(){return false;}public boolean buttonsReady(){return ready;}
        public void connect(BoardTarget target){connects++;ready=true;status.accept("BLE 연결됨");changed.run();}
        public boolean send(String value){if(!ready)return false;sent.add(value);return true;}
        public void close(){closes++;ready=false;changed.run();}
        void drop(){ready=false;changed.run();status.accept("BLE 연결 종료");}
    }
    private ServiceController<TestBoardService> controller;private TestBoardService board;
    private ServiceController<MonitorService> monitorController;
    private ServiceController<AssistantService> assistantController;
    @Before public void start(){
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO);
        controller=Robolectric.buildService(TestBoardService.class).create();board=controller.get();board.onStartCommand(connectIntent(ADDRESS),0,1);assertTrue(BoardService.ready());
    }
    private Intent connectIntent(String address){return new Intent(board,BoardService.class).setAction(BoardService.CONNECT).putExtra("address",address).putExtra("service",BleLink.DEFAULT_SERVICE).putExtra("characteristic",BleLink.DEFAULT_CHARACTERISTIC);}
    private void startMonitor(){
        monitorController=Robolectric.buildService(MonitorService.class).create();
        MonitorConfig config=new MonitorConfig("","설명",GeminiClient.DEFAULT_MODEL,true,true,true,false,2000,"",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC,CameraSource.BACK);
        monitorController.get().onStartCommand(config.write(new Intent().setAction("dev.giljabi.MONITOR_START")),0,1);
    }
    private void finishMonitor()throws Exception{
        if(monitorController==null)return;
        monitorController.get().onStartCommand(new Intent().setAction("dev.giljabi.MONITOR_STOP"),0,2);monitorController.destroy();monitorController=null;
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(MonitorService.active()&&System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
        shadowOf(Looper.getMainLooper()).idle();assertFalse("AI cleanup did not finish",MonitorService.active());
    }
    @After public void finish()throws Exception{finishMonitor();if(assistantController!=null){assistantController.destroy();assistantController=null;}board.onStartCommand(new Intent().setAction(BoardService.DISCONNECT),0,99);controller.destroy();shadowOf(Looper.getMainLooper()).idle();}
    @Test public void repeatedConnectDoesNotReplaceWorkingGatt()throws Exception{BoardService.connect(board,ADDRESS,BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC);assertEquals(1,board.fake.connects);assertEquals(0,board.fake.closes);assertTrue(BoardService.ready());}
    @Test public void changingEndpointExplicitlyClosesPreviousConnection(){board.onStartCommand(connectIntent("AA:BB:CC:DD:EE:02"),0,2);assertEquals(1,board.fake.closes);assertEquals(2,board.fake.connects);assertEquals("AA:BB:CC:DD:EE:02",BoardService.snapshot().address);}
    @Test public void leavingAndDestroyingActivityKeepsBoardConnection(){ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).setup();activity.pause().stop().destroy();assertTrue(BoardService.ready());assertEquals(0,board.fake.closes);assertTrue(BoardService.send("TEST\n"));assertEquals(Collections.singletonList("TEST\n"),board.fake.sent);}
    @Test public void stoppingRealTimeAiKeepsBoardAndTestWorks()throws Exception{startMonitor();finishMonitor();assertTrue(BoardService.ready());assertEquals(0,board.fake.closes);assertTrue(BoardService.send("TEST\n"));}
    @Test public void stoppingVoiceAssistantKeepsBoard(){assistantController=Robolectric.buildService(AssistantService.class).create();assistantController.get().onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_START"),0,1);assistantController.get().onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_STOP"),0,2);assistantController.destroy();assistantController=null;assertTrue(BoardService.ready());assertEquals(0,board.fake.closes);}
    @Test public void unexpectedDropReconnectsWithoutReplayingMotorCommands(){assertTrue(BoardService.send("OBSTACLE\n"));board.fake.drop();assertFalse(BoardService.send("TEST\n"));shadowOf(Looper.getMainLooper()).idleFor(10,TimeUnit.SECONDS);assertTrue(BoardService.ready());assertEquals(2,board.fake.connects);assertEquals(Collections.singletonList("OBSTACLE\n"),board.fake.sent);}
    @Test public void explicitDisconnectCancelsReconnect(){board.onStartCommand(new Intent().setAction(BoardService.DISCONNECT),0,2);shadowOf(Looper.getMainLooper()).idleFor(30,TimeUnit.SECONDS);assertFalse(BoardService.active());assertEquals(1,board.fake.connects);assertEquals(1,board.fake.closes);}
    @Test public void buttonWithoutAssistantDoesNotStartRecording(){board.fake.received.accept("ASSIST");assertTrue(BoardService.ready());assertFalse(AssistantService.active());assertTrue(BoardService.snapshot().status.contains("버튼 신호 수신"));}
    @Test public void uiTestUsesConnectedBoardWhileCameraConfigHasNoMac(){ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).setup();try{startMonitor();Button test=(Button)find(activity.get().getWindow().getDecorView());assertNotNull(test);assertTrue(test.isEnabled());test.performClick();assertEquals(Collections.singletonList("TEST\n"),board.fake.sent);}finally{activity.pause().stop().destroy();}}
    private View find(View view){if(view instanceof Button&&"아두이노 TEST 신호 보내기".contentEquals(((Button)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i));if(found!=null)return found;}}return null;}
}
