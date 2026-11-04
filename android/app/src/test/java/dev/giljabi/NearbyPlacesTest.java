package dev.giljabi;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;import java.io.IOException;
public class NearbyPlacesTest {
 @Test public void realCoordinatesAreSortedAndNamed()throws Exception{String json="{\"elements\":[{\"lat\":37.001,\"lon\":127,\"tags\":{\"name\":\"둘째병원\"}},{\"center\":{\"lat\":37.0001,\"lon\":127},\"tags\":{\"name:ko\":\"첫째의원\"}},{\"lat\":37,\"lon\":127,\"tags\":{}}]}";List<NearbyPlaces.Place> p=NearbyPlaces.parse(json,37,127);assertEquals(2,p.size());assertEquals("첫째의원",p.get(0).name);assertTrue(p.get(0).url().startsWith("https://www.google.com/maps/"));}
 @Test public void missingResultsDoNotClaimNoHospital()throws Exception{assertTrue(NearbyPlaces.spoken("병원",NearbyPlaces.parse("{\"elements\":[]}",37,127)).contains("없다는 뜻은 아니"));}
 @Test public void farOrInvalidCoordinatesAreDropped()throws Exception{assertTrue(NearbyPlaces.parse("{\"elements\":[{\"lat\":90,\"lon\":180,\"tags\":{\"name\":\"멀리\"}}]}",37,127).isEmpty());}
 @Test public void queryCannotInjectServerSyntax()throws Exception{try{NearbyPlaces.query("hospital;out;",37,127);fail();}catch(IOException expected){}try{NearbyPlaces.query("병원",Double.NaN,127);fail();}catch(IOException expected){}}
 @Test public void haversineIsPlausible(){assertEquals(111,NearbyPlaces.distance(37,127,37.001,127),1);}
 @Test public void aLongResultDoesNotCutOffTheNextHospitalNameAndDistance(){List<NearbyPlaces.Place> p=Arrays.asList(new NearbyPlaces.Place("가".repeat(100),"",37,127,200),new NearbyPlaces.Place("나".repeat(100),"",37,127,500));String spoken=AssistantCommands.speechText(NearbyPlaces.spoken("병원",p));assertTrue(spoken.contains("200미터"));assertFalse(spoken.contains("나"));assertFalse(spoken.contains("더 궁금하면"));}
}
