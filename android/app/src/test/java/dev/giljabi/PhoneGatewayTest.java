package dev.giljabi;

import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ServiceController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,shadows=PhoneGatewayTest.PhoneShadow.class)
public class PhoneGatewayTest {
    @Implements(TelecomManager.class)
    public static class PhoneShadow {
        static Uri target;static int count;
        @Implementation protected void placeCall(Uri address,Bundle extras){target=address;count++;}
    }
    @Test public void confirmedGatewayUsesSystemTelecomWithExactNumber(){
        PhoneShadow.target=null;PhoneShadow.count=0;
        ServiceController<AssistantService> controller=Robolectric.buildService(AssistantService.class).create();
        try{controller.get().placePhoneCall("01012345678");assertEquals(1,PhoneShadow.count);assertEquals("tel",PhoneShadow.target.getScheme());assertEquals("01012345678",PhoneShadow.target.getSchemeSpecificPart());}
        finally{controller.destroy();}
    }
}
