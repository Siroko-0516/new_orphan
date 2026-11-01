package dev.giljabi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WalkingGuidanceTest {
    @org.junit.Test public void localCaptionDoesNotTurnSceneryIntoPathWarning(){
        org.junit.Assert.assertFalse(WalkingGuidance.localUseful("A rocky outcrop partly blocks the view of a valley.","no_confirmed_path_cues"));
        org.junit.Assert.assertFalse(WalkingGuidance.localUseful("A mountain valley is visible.","no_confirmed_path_cues"));
        org.junit.Assert.assertTrue(WalkingGuidance.localUseful("A chair is blocking the walkway.","seat:lower:path"));
        org.junit.Assert.assertTrue(WalkingGuidance.localUseful("A hanging branch crosses the path.","no_confirmed_path_cues"));
    }

    @org.junit.Test public void freshAdmissionAndBoundedSentenceCompletion(){
        org.junit.Assert.assertTrue(WalkingGuidance.speechAge(1000,5900,false));
        org.junit.Assert.assertFalse(WalkingGuidance.speechAge(1000,6001,false));
        org.junit.Assert.assertTrue(WalkingGuidance.speechAge(1000,7000,true));
        org.junit.Assert.assertFalse(WalkingGuidance.speechAge(1000,13001,true));
    }

    private String json(boolean speak,String message)throws Exception{return new JSONObject().put("speak",speak).put("message",message).toString();}
    @Test public void visibleWorseningCanBeMarkedUrgent()throws Exception{String text=new JSONObject().put("speak",true).put("message","전방 통로가 의자 때문에 이전보다 더 가려진 것 같아요.").put("urgency","urgent").toString();WalkingGuidance.Reply reply=WalkingGuidance.parse(text);assertTrue(reply.valid);assertTrue(reply.speak);assertTrue(reply.urgent);assertTrue(WalkingGuidance.completeJson(text));}
    @Test public void legacyNormalNoticeAndSilenceAreNotUrgent()throws Exception{assertFalse(WalkingGuidance.parse(json(true,"전방 통로에 의자가 보여요.")).urgent);String notice=new JSONObject().put("speak",true).put("message","통로 옆에 의자가 보여요.").put("urgency","notice").toString();assertTrue(WalkingGuidance.parse(notice).valid);assertFalse(WalkingGuidance.parse(notice).urgent);String silence=new JSONObject().put("speak",false).put("message","").put("urgency","none").toString();assertTrue(WalkingGuidance.parse(silence).valid);assertFalse(WalkingGuidance.parse(silence).urgent);}
    @Test public void acceptsReversedThreeFieldOrder(){String text="{\"urgency\":\"urgent\",\"message\":\"통로가 의자로 더 가려진 것 같아요.\",\"speak\":true}";assertTrue(WalkingGuidance.parse(text).urgent);assertTrue(WalkingGuidance.completeJson(text));}
    @Test public void rejectsInvalidUrgencyTypesAndDuplicateOrExtraKeys(){for(String text:new String[]{"{\"speak\":true,\"message\":\"앞에 의자가 있어요.\",\"urgency\":\"critical\"}","{\"speak\":true,\"message\":\"앞에 의자가 있어요.\",\"urgency\":true}","{\"speak\":true,\"message\":\"앞에 의자가 있어요.\",\"urgency\":\"notice\",\"urgency\":\"urgent\"}","{\"speak\":true,\"message\":\"앞에 의자가 있어요.\",\"urgency\":\"urgent\",\"extra\":0}"}){assertFalse(WalkingGuidance.parse(text).valid);assertFalse(WalkingGuidance.completeJson(text));}}
    @Test public void urgencyDoesNotBypassDistanceOrDetectorGuards()throws Exception{for(String message:new String[]{"2m 앞의 의자가 위험해요.","화면 중앙 의자 감지."}){String text=new JSONObject().put("speak",true).put("message",message).put("urgency","urgent").toString();assertFalse(WalkingGuidance.parse(text).valid);}}
    @Test public void freshFramesHaveAnAbsoluteFiveSecondLimit(){assertTrue(WalkingGuidance.fresh(1000,6000));assertFalse(WalkingGuidance.fresh(1000,6001));assertFalse(WalkingGuidance.fresh(1001,1000));assertFalse(WalkingGuidance.fresh(-1,0));}
    @Test public void acceptsExplicitSilenceWithoutPretendingItIsMalformed()throws Exception{WalkingGuidance.Reply r=WalkingGuidance.parse(json(false,""));assertTrue(r.valid);assertFalse(r.speak);assertEquals("",r.message);}
    @Test public void acceptsNaturalCameraRelativeGuidance()throws Exception{WalkingGuidance.Reply r=WalkingGuidance.parse(json(true,"앞쪽에 의자가 보여요."));assertTrue(r.valid);assertTrue(r.speak);assertEquals("앞쪽에 의자가 보여요.",r.message);}
    @Test public void permitsUpperImageObservationWithoutInventingHeadHeight()throws Exception{assertTrue(WalkingGuidance.parse(json(true,"앞쪽 위에 간판이 보여요.")).valid);assertFalse(WalkingGuidance.parse(json(true,"머리 높이에 간판이 있어요.")).valid);}
    @Test public void rejectsFabricatedMetricDistances()throws Exception{for(String value:new String[]{"전방 약 2m에 의자가 있어요.","약 2 미터에 의자가 있어요.","두 미터 앞에 의자가 보여요.","전방 ２ｍ에 의자가 보여요.","2\u200bm 앞에 의자가 있어요.","A chair is two meters ahead.","30cm 앞에 장애물이 있어요.","두 걸음 앞에 의자가 있어요.","한 발짝 앞에 의자가 있어요.","A chair is two steps ahead.","An obstacle is 3 paces ahead."})assertFalse(value,WalkingGuidance.parse(json(true,value)).valid);}
    @Test public void rejectsUiCoordinatesAndUnverifiedMovementCommands()throws Exception{for(String value:new String[]{"화면 중앙에 의자가 보여요.","오른쪽으로 가세요.","계속 직진하세요."})assertFalse(WalkingGuidance.parse(json(true,value)).valid);assertTrue(WalkingGuidance.parse(json(true,"전방 통로에 의자가 있는 것 같아요.")).valid);}
    @Test public void rejectsOldDetectorBoilerplate()throws Exception{for(String value:new String[]{"화면 중앙에 의자 감지.","감지 감지 감지", "chair detected", "Object detection: person, chair"}){WalkingGuidance.Reply r=WalkingGuidance.parse(json(true,value));assertFalse(r.valid);assertFalse(r.speak);}}
    @Test public void rejectsCaneCapabilityAndAllClearClaims()throws Exception{for(String value:new String[]{"흰지팡이로 감지하지 못하므로 위험해요.","지팡이로 확인할 수 있어요.","장애물이 없으니 안전합니다.","The path is clear.","전방 통로가 비어 있어요."})assertFalse(WalkingGuidance.parse(json(true,value)).valid);}
    @Test public void malformedMarkdownTrailingDataAndWrongTypesAreInvalid(){for(String value:new String[]{null,"", "앞에 의자가 보여요.", "```json\n{\"speak\":true,\"message\":\"앞에 의자가 보여요.\"}\n```", "{\"speak\":true,\"message\":\"안내\"} extra", "{\"speak\":\"true\",\"message\":\"안내\"}", "{speak:true,message:'안내'}", "{\"speak\":true,\"message\":\"안내\",\"extra\":0}", "{\"speak\":true}", "{\"speak\":false,\"speak\":true,\"message\":\"안내\"}"}){WalkingGuidance.Reply r=WalkingGuidance.parse(value);assertFalse(r.valid);assertFalse(r.speak);}}
    @Test public void refusesLongOrEmptySpokenMessages()throws Exception{assertFalse(WalkingGuidance.parse(json(true,"")).valid);assertFalse(WalkingGuidance.parse(json(true,"가".repeat(301))).valid);}
    @Test public void stopsOnCompleteJsonEvenWhenPolicyWillRejectIt()throws Exception{String text=json(true,"2m 앞에 의자가 있어요.");assertTrue(WalkingGuidance.completeJson(text));assertFalse(WalkingGuidance.parse(text).valid);assertFalse(WalkingGuidance.completeJson("{\"speak\":true,\"message\":\"문장."));assertFalse(WalkingGuidance.completeJson("```json\n"+text+"\n```"));}
    @Test public void escapedQuotesAndReversedKeyOrderAreValid(){WalkingGuidance.Reply r=WalkingGuidance.parse(" { \"message\" : \"앞쪽에 \\\"출입문\\\"이 보여요.\", \"speak\" : true } ");assertTrue(r.valid);assertTrue(r.speak);}
    @Test public void promptCarriesCaneMountingContextAndPreviousSpokenEvidence(){String p=WalkingGuidance.prompt("주변을 알려줘", "chair center", "앞쪽에 의자가 보여요.",1234);assertTrue(p.contains("좌우 스윙"));assertTrue(p.contains("실제 진행 방향"));assertTrue(p.contains("아래쪽·중간·위쪽"));assertTrue(p.contains("previous_spoken_guidance"));assertTrue(p.contains("distance_measurement_available"));assertTrue(p.contains("false"));assertTrue(p.contains("1234"));assertTrue(p.contains("약 두 미터"));}
}
