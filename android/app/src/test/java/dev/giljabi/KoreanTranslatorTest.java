package dev.giljabi;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class KoreanTranslatorTest {
    private static final class Backend implements KoreanTranslator.Backend {
        int downloads,checks,translations;boolean cached=true;
        Task<String> answer=Tasks.forResult("앞쪽 통로에 의자가 있어요.");
        Task<Void> download=Tasks.forResult(null);
        @Override public Task<Boolean> ready(){checks++;return Tasks.forResult(cached);}
        @Override public Task<Void> download(){downloads++;return download;}
        @Override public Task<String> translate(String text){translations++;return answer;}
    }
    private KoreanTranslator translator(Backend backend){return new KoreanTranslator(backend,Runnable::run,500);}
    @Test public void detectsEnglishSentencesAndMixedEnglishCaptions(){
        assertTrue(KoreanTranslator.needsTranslation("A chair is in front of the path."));
        assertTrue(KoreanTranslator.needsTranslation("Chair ahead."));
        assertTrue(KoreanTranslator.needsTranslation("앞쪽에 A person is standing near the doorway."));
    }
    @Test public void koreanSentencesAndProductNamesAreAlreadyReady(){
        assertFalse(KoreanTranslator.needsTranslation("앞쪽 통로에 의자가 보여요."));
        assertFalse(KoreanTranslator.needsTranslation("의자 옆에 TV와 USB 케이블이 있어요."));
        assertFalse(KoreanTranslator.needsTranslation("123.45"));assertFalse(KoreanTranslator.needsTranslation(""));
        assertFalse(KoreanTranslator.needsTranslation(null));
    }
    @Test public void identifiersAndUrlsAreNotTranslationInput(){
        assertFalse(KoreanTranslator.needsTranslation("LS3HmRGCXV8wxCAhUbTt"));
        assertFalse(KoreanTranslator.needsTranslation("ESP32 CAM CH340"));
        assertFalse(KoreanTranslator.needsTranslation("https://example.invalid/a/path"));
        assertFalse(KoreanTranslator.needsTranslation("{\"speak\":false,\"message\":\"\"}"));
    }
    @Test public void existingKoreanBypassesModelChecksAndDownloads()throws Exception{
        Backend b=new Backend();b.cached=false;String korean="앞쪽에 의자가 보여요.";
        assertEquals(korean,translator(b).translateIfNeeded(korean,()->false,text->{}));
        assertEquals(0,b.downloads);assertEquals(0,b.checks);assertEquals(0,b.translations);
    }
    @Test public void absentModelNeverDownloadsDuringTranslation(){
        Backend b=new Backend();b.cached=false;
        IOException e=assertThrows(IOException.class,()->translator(b).translateIfNeeded("A chair is ahead.",()->false,text->{}));
        assertTrue(e.getMessage().contains("오프라인 한국어 번역 준비"));assertEquals(0,b.downloads);assertEquals(0,b.translations);
    }
    @Test public void explicitPreparationDownloadsAndReportsCompletion(){
        Backend b=new Backend();List<String> messages=new ArrayList<>();translator(b).prepare(messages::add);
        assertEquals(1,b.downloads);assertEquals(2,messages.size());assertTrue(messages.get(1).contains("준비 완료"));
    }
    @Test public void duplicatePreparationDoesNotLaunchAnotherDownload(){
        Backend b=new Backend();b.download=new TaskCompletionSource<Void>().getTask();KoreanTranslator t=translator(b);List<String> messages=new ArrayList<>();
        t.prepare(messages::add);t.prepare(messages::add);assertEquals(1,b.downloads);assertTrue(messages.get(1).contains("이미 준비"));
    }
    @Test public void cachedTranslationDoesNotDownload()throws Exception{
        Backend b=new Backend();assertEquals("앞쪽 통로에 의자가 있어요.",translator(b).translateIfNeeded("A chair is ahead.",()->false,text->{}));
        assertEquals(0,b.downloads);assertEquals(1,b.checks);assertEquals(1,b.translations);
    }
    @Test public void cancellationBeforeTranslationDoesNoWork(){
        Backend b=new Backend();assertThrows(CancellationException.class,()->translator(b).translateIfNeeded("A chair is ahead.",()->true,text->{}));assertEquals(0,b.checks);assertEquals(0,b.downloads);assertEquals(0,b.translations);
    }
    @Test public void inFlightTranslationIsBounded(){
        Backend b=new Backend();b.answer=new TaskCompletionSource<String>().getTask();KoreanTranslator t=new KoreanTranslator(b,Runnable::run,30);
        IOException e=assertThrows(IOException.class,()->t.translateIfNeeded("A chair is ahead.",()->false,text->{}));assertTrue(e.getMessage().contains("시간이 초과"));assertEquals(0,b.downloads);
    }
    @Test public void cancellationDuringTranslationDropsItsResult(){
        Backend b=new Backend();b.answer=new TaskCompletionSource<String>().getTask();AtomicBoolean cancelled=new AtomicBoolean();
        assertThrows(CancellationException.class,()->translator(b).translateIfNeeded("A chair is ahead.",cancelled::get,text->cancelled.set(true)));assertEquals(0,b.downloads);
    }
    @Test public void failedTaskAndEnglishOutputAreNeverSpokenAsKorean(){
        Backend b=new Backend();b.answer=Tasks.forException(new IllegalStateException("missing model"));assertThrows(IOException.class,()->translator(b).translateIfNeeded("A chair is ahead.",()->false,text->{}));
        b.answer=Tasks.forResult("A chair is ahead.");assertThrows(IOException.class,()->translator(b).translateIfNeeded("A chair is ahead.",()->false,text->{}));
        b.answer=Tasks.forResult(" ");assertThrows(IOException.class,()->translator(b).translateIfNeeded("A chair is ahead.",()->false,text->{}));assertEquals(0,b.downloads);
    }
}
