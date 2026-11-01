package dev.giljabi;
import org.junit.Test;
import static org.junit.Assert.*;
public class ApiKeyInputTest {
 private static final String KEY="fake-personal-key-for-test_12345";
 @Test public void leavesPlainKeyUnchanged(){ApiKeyInput.Parsed p=ApiKeyInput.parse(KEY);assertEquals(KEY,p.value);assertNull(p.problem);assertFalse(p.cleaned);}
 @Test public void cleansPasteWrappingAndUnicodeSpaces(){ApiKeyInput.Parsed p=ApiKeyInput.parse("\r\n \u00a0"+KEY.substring(0,12)+"\n\t\u202f"+KEY.substring(12)+"\u3000");assertEquals(KEY,p.value);assertNull(p.problem);assertTrue(p.cleaned);}
 @Test public void removesZeroWidthBomAndDirectionFormatting(){assertEquals(KEY,ApiKeyInput.parse("\ufeff\u2066"+KEY.substring(0,10)+"\u200b\u200e"+KEY.substring(10)+"\u2069").value);}
 @Test public void acceptsMatchingClipboardQuotes(){for(String q:new String[]{"'","\"","`"})assertEquals(KEY,ApiKeyInput.parse(q+KEY+q).value);}
 @Test public void acceptsDotInOpaqueCredential(){assertNull(ApiKeyInput.parse("AQ.fake-test-key.with-dots_12345").problem);}
 @Test public void doesNotGuessOpaqueCredentialAlphabet(){assertNull(ApiKeyInput.parse("fake.token+with/symbols==").problem);assertNull(ApiKeyInput.parse("opaque-normal-key:token").problem);}
 @Test public void maskedKeyGetsSpecificError(){assertTrue(ApiKeyInput.parse("fake-test-key-****1234").problem.contains("가려지"));assertTrue(ApiKeyInput.parse("fake...test-key-1234").problem.contains("생략"));}
 @Test public void doesNotGuessReplacementForUnicodeHyphen(){ApiKeyInput.Parsed p=ApiKeyInput.parse(KEY.replace('-','\u2011'));assertTrue(p.problem.contains("U+2011"));assertEquals("",p.value);assertFalse(p.problem.contains(KEY));}
 @Test public void copiedCodeAndHeaderInjectionAreRejected(){assertNotNull(ApiKeyInput.parse("GEMINI_API_KEY="+KEY).problem);assertNotNull(ApiKeyInput.parse(KEY+"\r\nInjected: x").problem);assertFalse(GeminiClient.validKey(KEY+"\r\nInjected: x"));}
 @Test public void lengthFailureDoesNotClaimWhitespace(){assertTrue(ApiKeyInput.parse("short").problem.contains("길이"));assertFalse(ApiKeyInput.parse("short").problem.contains("공백"));}
 @Test public void emptyInputCanKeepPreviouslySavedKey(){assertEquals("",ApiKeyInput.parse("").value);assertNull(ApiKeyInput.parse("").problem);}
}
