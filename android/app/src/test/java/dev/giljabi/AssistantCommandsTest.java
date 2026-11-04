package dev.giljabi;
import org.junit.Test;import static org.junit.Assert.*;
public class AssistantCommandsTest {
 @Test public void explicitContactCall(){AssistantCommands.Command c=AssistantCommands.parse("엄마에게 전화해 줘",false);assertEquals(AssistantCommands.Kind.CALL,c.kind);assertEquals("엄마",c.target);}
 @Test public void callWithSpacedParticles(){assertEquals("아빠",AssistantCommands.parse("아빠 한테 전화 걸어줘",false).target);}
 @Test public void negativeCannotCall(){assertEquals(AssistantCommands.Kind.CANCEL,AssistantCommands.parse("엄마에게 전화하지마",true).kind);}
 @Test public void phoneNumberQuestionIsNotCall(){assertEquals(AssistantCommands.Kind.CHAT,AssistantCommands.parse("엄마 전화번호 알려줘",false).kind);}
 @Test public void yesOnlyInPendingConfirmation(){assertEquals(AssistantCommands.Kind.CONFIRM,AssistantCommands.parse("네",true).kind);assertEquals(AssistantCommands.Kind.CHAT,AssistantCommands.parse("네",false).kind);}
 @Test public void mixedYesNoCannotConfirm(){assertNotEquals(AssistantCommands.Kind.CONFIRM,AssistantCommands.parse("네 아니요",true).kind);}
 @Test public void nearbyHospital(){assertEquals(AssistantCommands.Kind.NEARBY,AssistantCommands.parse("이 주변에 병원 있어?",false).kind);assertEquals("병원",AssistantCommands.parse("가까운 의원 찾아줘",false).target);}
 @Test public void medicalGeneralQuestionIsNotSearch(){assertEquals(AssistantCommands.Kind.CHAT,AssistantCommands.parse("병원 감염 예방방법 알려줘",false).kind);}
 @Test public void normalizedPhoneAllowsOrdinaryNumber(){assertEquals("+821012345678",AssistantCommands.phone("+82 (10) 1234-5678"));}
 @Test public void dialControlCodesAreRejected(){assertEquals("",AssistantCommands.phone("*21*01012345678#"));assertEquals("",AssistantCommands.phone("01012345678,1"));assertEquals("",AssistantCommands.phone("119"));}
 @Test public void contactAliasesAndEmoji(){assertTrue(AssistantCommands.sameName("엄마❤️","어머니"));assertFalse(AssistantCommands.sameName("엄마회사","엄마"));}
 @Test public void outputIsNotAnActionInput(){assertTrue(AssistantCommands.speechText("**안녕하세요** https://example.com").startsWith("안녕하세요"));}
 @Test public void currentLocationIsADeviceAction(){for(String text:new String[]{"내 위치가 어디야","현재 위치 알려줘","여기가 어딘지 알려줘","나 어디야?","내가 있는 곳 알려줘","현재 주소 확인해줘"})assertEquals(text,AssistantCommands.Kind.LOCATION,AssistantCommands.parse(text,false).kind);}
 @Test public void nearbySearchTakesPriorityOverLocationWords(){assertEquals(AssistantCommands.Kind.NEARBY,AssistantCommands.parse("내 위치 주변에 병원 있냐",false).kind);assertEquals(AssistantCommands.Kind.NEARBY,AssistantCommands.parse("병원 있냐",false).kind);}
 @Test public void locationNegationNeverQueriesGps(){assertEquals(AssistantCommands.Kind.CANCEL,AssistantCommands.parse("내 위치 조회하지마",false).kind);}
 @Test public void callWithoutANameExplainsActualCapability(){assertEquals(AssistantCommands.Kind.CALL_HELP,AssistantCommands.parse("전화해줘",false).kind);}
 @Test public void conversationalRepliesApplyOnlyToPendingCall(){for(String s:new String[]{"네 전화해줘","응 걸어줘","그래","좋아 연결해줘"})assertEquals(s,AssistantCommands.Kind.CONFIRM,AssistantCommands.parse(s,true).kind);for(String s:new String[]{"아니","아니오","안해","안할래","괜찮아"})assertEquals(s,AssistantCommands.Kind.CANCEL,AssistantCommands.parse(s,true).kind);assertEquals(AssistantCommands.Kind.CHAT,AssistantCommands.parse("괜찮아",false).kind);}
 @Test public void politeContactNamesAreUnderstood(){assertEquals("엄마",AssistantCommands.callTarget("엄마한테요"));assertEquals("아빠",AssistantCommands.callTarget("아빠요"));}
}
