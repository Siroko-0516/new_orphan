package dev.giljabi;

import android.Manifest;
import android.app.UiAutomation;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.view.KeyEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android contacts, Telecom and dialer; strictly restricted to the AVD modem. */
@RunWith(AndroidJUnit4.class)
public class PhoneSystemSmokeTest {
    private static final String NAME="길잡이 발신 검사",NUMBER="+12025550123"; // NANPA fictional 555-0123.
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.CALL_PHONE,Manifest.permission.READ_CONTACTS);
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static final class PhoneProbe extends AssistantService {
        PhoneProbe(Context context){attachBaseContext(context);}
    }
    private Uri insertContact()throws Exception{
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS);
        try{
            ArrayList<ContentProviderOperation> batch=new ArrayList<>();
            batch.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI).withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
            batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0).withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,NAME).build());
            batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0).withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,NUMBER).withValue(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
            return context.getContentResolver().applyBatch(ContactsContract.AUTHORITY,batch)[0].uri;
        }finally{automation.dropShellPermissionIdentity();}
    }
    private boolean systemHasCall(TelecomManager telecom){
        automation.adoptShellPermissionIdentity(Manifest.permission.READ_PHONE_STATE);
        try{return telecom.isInCall();}finally{automation.dropShellPermissionIdentity();}
    }
    private void endCall(TelecomManager telecom){
        device.pressKeyCode(KeyEvent.KEYCODE_ENDCALL);
        automation.adoptShellPermissionIdentity(Manifest.permission.ANSWER_PHONE_CALLS);
        try{telecom.endCall();}catch(RuntimeException ignored){}finally{automation.dropShellPermissionIdentity();}
    }
    @Test public void appGatewayStartsARealSystemCallAndShowsTheDialer()throws Exception{
        // Never dial on a physical handset, including when someone runs connectedAndroidTest manually.
        Assume.assumeTrue("Only run against an Android emulator",Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        assertEquals(PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(Manifest.permission.CALL_PHONE));
        TelecomManager telecom=context.getSystemService(TelecomManager.class);
        assertNotNull(telecom);assertFalse("Emulator must start without a call",systemHasCall(telecom));
        String dialer=telecom.getDefaultDialerPackage();assertNotNull("Emulator has no system dialer",dialer);
        File folder=new File(context.getFilesDir(),"phone-system");assertTrue(folder.isDirectory()||folder.mkdirs());
        Uri contact=null;
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            contact=insertContact();
            PhoneProbe app=new PhoneProbe(context);
            AssistantContacts.Contact found=app.findPhoneContact(NAME);
            assertEquals(NUMBER,found.number);
            assertEquals(AssistantCommands.Kind.CALL,AssistantCommands.parse(NAME+"에게 전화해줘",false).kind);
            // This calls the production method with the app's CALL_PHONE permission, with no Telecom mock.
            app.placePhoneCall(found.number);
            long deadline=SystemClock.elapsedRealtime()+15000;
            while(!systemHasCall(telecom)&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
            assertTrue("Android Telecom never registered the outgoing call; UI="+device.getCurrentPackageName(),systemHasCall(telecom));
            assertTrue("System dialer did not open; expected="+dialer+", actual="+device.getCurrentPackageName(),device.wait(Until.hasObject(By.pkg(dialer)),15000));
            SystemClock.sleep(1200);
            assertTrue("Cannot capture system phone screen",device.takeScreenshot(new File(folder,"phone-system-screen.png")));
            device.dumpWindowHierarchy(new File(folder,"phone-system-window.xml"));
            JSONObject report=new JSONObject().put("android_api",Build.VERSION.SDK_INT).put("hardware",Build.HARDWARE).put("test_contact_lookup",true).put("production_phone_gateway",true).put("telecom_mocked",false).put("system_call_present",true).put("system_dialer_visible",true).put("dialer_package",dialer).put("fictional_test_number",NUMBER).put("real_mobile_network_used",false).put("real_recipient_connection_verified",false).put("voice_recognition_or_spoken_confirmation_tested_here",false);
            try(FileOutputStream output=new FileOutputStream(new File(folder,"phone-system-check.json"))){output.write((report.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));}
        }finally{
            endCall(telecom);
            if(contact!=null){automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS);try{context.getContentResolver().delete(contact,null,null);}finally{automation.dropShellPermissionIdentity();}}
        }
        long deadline=SystemClock.elapsedRealtime()+5000;
        while(systemHasCall(telecom)&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
        assertFalse("Test call did not end",systemHasCall(telecom));
    }
}
