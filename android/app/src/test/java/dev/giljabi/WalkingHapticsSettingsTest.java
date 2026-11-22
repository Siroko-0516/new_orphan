package dev.giljabi;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.CheckBox;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class WalkingHapticsSettingsTest {
    private SharedPreferences settings(){return RuntimeEnvironment.getApplication().getSharedPreferences("ai-settings",Context.MODE_PRIVATE);}
    private CheckBox toggle(MainActivity activity)throws Exception{Field f=MainActivity.class.getDeclaredField("notifyObjects");f.setAccessible(true);return (CheckBox)f.get(activity);}
    private MonitorConfig config(MainActivity activity)throws Exception{Method m=MainActivity.class.getDeclaredMethod("monitorConfig");m.setAccessible(true);return (MonitorConfig)m.invoke(activity);}
    private void close(ActivityController<MainActivity> controller){controller.pause().stop().destroy();}
    @Test public void newInstallationEnablesWalkingAlertsForEveryProvider()throws Exception{
        for(String provider:new String[]{AiProvider.OPENAI,AiProvider.GEMINI,AiProvider.LOCAL}){
            settings().edit().clear().putString("vision_provider",provider).commit();
            ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).setup();
            try{assertTrue(toggle(activity.get()).isChecked());MonitorConfig c=config(activity.get());assertEquals(provider,c.provider);assertTrue(c.notify);}finally{close(activity);}
        }
    }
    @Test public void explicitOptOutSurvivesActivityRecreation()throws Exception{
        settings().edit().clear().putString("vision_provider",AiProvider.OPENAI).commit();
        ActivityController<MainActivity> first=Robolectric.buildActivity(MainActivity.class).setup();
        try{toggle(first.get()).setChecked(false);assertFalse(config(first.get()).notify);}finally{close(first);}
        ActivityController<MainActivity> reopened=Robolectric.buildActivity(MainActivity.class).setup();
        try{assertFalse(toggle(reopened.get()).isChecked());assertFalse(config(reopened.get()).notify);}finally{close(reopened);}
    }
    @Test public void reEnablingPersistsAndReachesTheGptMonitoringConfig()throws Exception{
        settings().edit().clear().putString("vision_provider",AiProvider.OPENAI).putBoolean("walking_haptics",false).commit();
        ActivityController<MainActivity> first=Robolectric.buildActivity(MainActivity.class).setup();
        try{toggle(first.get()).setChecked(true);}finally{close(first);}
        ActivityController<MainActivity> reopened=Robolectric.buildActivity(MainActivity.class).setup();
        try{assertTrue(toggle(reopened.get()).isChecked());MonitorConfig c=config(reopened.get());assertEquals(AiProvider.OPENAI,c.provider);assertTrue(c.notify);}finally{close(reopened);}
    }
}
