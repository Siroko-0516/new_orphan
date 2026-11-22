package dev.giljabi;
import org.junit.Test;
import static org.junit.Assert.*;
public class WalkingSpeechTextTest {
 @Test public void readsTheFirstCompleteFactWithoutClothingAndBackgroundExplanation(){assertEquals("앞쪽 통로에 사람이 있어요.",WalkingSpeechText.brief("앞쪽 통로에 사람이 있어요. 검은 옷을 입고 가방을 들고 있습니다. 배경에는 건물이 보입니다."));}
 @Test public void anAlreadyShortSentenceIsKept(){assertEquals("통로 위로 나뭇가지가 나와 있어요.",WalkingSpeechText.brief("통로 위로 나뭇가지가 나와 있어요."));}
 @Test public void englishIsShortenedBeforeTranslation(){assertEquals("A person is in the walkway.",WalkingSpeechText.brief("A person is in the walkway. They are wearing a dark coat."));}
 @Test public void punctuationDoesNotCutADecimalMidSentence(){assertEquals("The image has version 1.2 data.",WalkingSpeechText.brief("The image has version 1.2 data. More detail."));}
 @Test public void noPunctuationDoesNotCreateABrokenSentence(){assertEquals("앞쪽 통로에 사람이 있어요",WalkingSpeechText.brief(" 앞쪽  통로에 사람이 있어요 "));assertEquals("",WalkingSpeechText.brief(null));}
 @Test public void sentencesWithoutAnInterveningSpaceAreStillShortened(){assertEquals("앞쪽 통로에 사람이 있어요.",WalkingSpeechText.brief("앞쪽 통로에 사람이 있어요.검은 옷을 입었습니다."));}
 @Test public void acknowledgementDoesNotReplaceTheActualObservation(){assertEquals("앞쪽에 사람이 있어요.",WalkingSpeechText.brief("네. 앞쪽에 사람이 있어요. 검은 옷입니다."));assertEquals("A person is in the walkway.",WalkingSpeechText.brief("Yes. A person is in the walkway. A building is behind."));assertEquals("네모난 물체가 통로에 있어요.",WalkingSpeechText.brief("네모난 물체가 통로에 있어요."));}
}
