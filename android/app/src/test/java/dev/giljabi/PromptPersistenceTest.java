package dev.giljabi;

import android.content.*;
import android.os.Looper;
import android.widget.*;
import java.lang.reflect.Field;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class PromptPersistenceTest {
    private ActivityController<MainActivity> activity;
    @Before public void open(){RuntimeEnvironment.getApplication().getSharedPreferences("ai-settings",Context.MODE_PRIVATE).edit().clear().commit();reopen();}
    @After public void close(){if(activity!=null)activity.pause().stop().destroy();}
    private void reopen(){if(activity!=null)activity.pause().stop().destroy();activity=Robolectric.buildActivity(MainActivity.class).setup();shadowOf(Looper.getMainLooper()).idle();}
    private <T>T field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);@SuppressWarnings("unchecked")T value=(T)f.get(activity.get());return value;}
    @Test public void customPromptSurvivesActivityDestructionAndInitialSpinnerCallback()throws Exception{EditText p=field("prompt");p.setText("걸어가는 통로의 사람과 장애물만 짧게 알려줘.");reopen();assertEquals("걸어가는 통로의 사람과 장애물만 짧게 알려줘.",((EditText)field("prompt")).getText().toString());}
    @Test public void languageChangesKeepIndependentDrafts()throws Exception{((EditText)field("prompt")).setText("내 한국어 질문");Spinner language=field("language");language.setSelection(0);shadowOf(Looper.getMainLooper()).idle();((EditText)field("prompt")).setText("My English question");language.setSelection(1);shadowOf(Looper.getMainLooper()).idle();assertEquals("내 한국어 질문",((EditText)field("prompt")).getText().toString());reopen();((Spinner)field("language")).setSelection(0);shadowOf(Looper.getMainLooper()).idle();assertEquals("My English question",((EditText)field("prompt")).getText().toString());}
    @Test public void providerChangesDoNotReplaceTheCustomPrompt()throws Exception{((EditText)field("prompt")).setText("내가 고른 보행 안내");RadioButton gpt=field("gptMode");gpt.setChecked(true);assertEquals("내가 고른 보행 안내",((EditText)field("prompt")).getText().toString());reopen();assertEquals("내가 고른 보행 안내",((EditText)field("prompt")).getText().toString());}
    @Test public void addressIntervalAndSpeechChoicesSurviveReopen()throws Exception{((EditText)field("ip")).setText("http://192.168.4.77:81/stream");((CheckBox)field("speak")).setChecked(false);((CheckBox)field("yoloEnabled")).setChecked(false);((Spinner)field("autoInterval")).setSelection(3);shadowOf(Looper.getMainLooper()).idle();reopen();assertEquals("http://192.168.4.77:81/stream",((EditText)field("ip")).getText().toString());assertFalse(((CheckBox)field("speak")).isChecked());assertFalse(((CheckBox)field("yoloEnabled")).isChecked());assertEquals(3,((Spinner)field("autoInterval")).getSelectedItemPosition());}
}
