package dev.giljabi;

import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;

public class MapLookupClientTest {
    static final class Connection extends HttpURLConnection {
        final int code;final String body;boolean closed;
        Connection(URL url,int code,String body){super(url);this.code=code;this.body=body;}
        public int getResponseCode(){return code;}public InputStream getInputStream(){return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));}
        public void disconnect(){closed=true;}public boolean usingProxy(){return false;}public void connect(){}
    }
    @Test public void reverseQueryUsesOnlyValidatedCoordinatesAndKorean()throws Exception{String u=MapLookupClient.reverse(37.5,127).toString();assertTrue(u.startsWith(MapLookupClient.BASE+"reverse?"));assertTrue(u.contains("accept-language=ko"));assertTrue(u.contains("lat=37.5&lon=127.0"));assertThrows(IOException.class,()->MapLookupClient.reverse(Double.NaN,127));}
    @Test public void addressReadsActualProviderAddressFields()throws Exception{String json="{\"address\":{\"state\":\"서울특별시\",\"city\":\"서울특별시\",\"city_district\":\"중구\",\"road\":\"테스트로\",\"house_number\":\"10\"}}";assertEquals("서울특별시 중구 테스트로 10",MapLookupClient.address(json));}
    @Test public void providerErrorsDoNotBecomeSpokenAddresses()throws Exception{assertEquals("",MapLookupClient.address("{\"error\":\"Unable to geocode\"}"));assertThrows(IOException.class,()->MapLookupClient.address("not json"));}
    @Test public void aCountryAloneIsNotTreatedAsTheCurrentStreetAddress()throws Exception{assertEquals("",MapLookupClient.address("{\"display_name\":\"대한민국\",\"address\":{\"country\":\"대한민국\"}}"));}
    @Test public void koreanDistrictsAndRuralTownNamesAreNotDropped()throws Exception{assertEquals("서울특별시 중구",MapLookupClient.address("{\"address\":{\"city\":\"서울특별시\",\"borough\":\"중구\"}}"));assertEquals("강원특별자치도 평창군 테스트면",MapLookupClient.address("{\"address\":{\"state\":\"강원특별자치도\",\"county\":\"평창군\",\"village\":\"테스트면\"}}"));}
    @Test public void categoryAndBoundsCannotInjectAQuery()throws Exception{String u=MapLookupClient.nearby("병원",37.5,127,false).toString();assertTrue(u.contains("bounded=1"));assertTrue(u.contains("%5Bhospital%5D"));assertTrue(MapLookupClient.nearby("병원",37.5,127,true).toString().contains("%5Bclinic%5D"));assertThrows(IOException.class,()->MapLookupClient.nearby("hospital;out;",37.5,127,false));}
    @Test public void medicalResultsHaveRealNamesAndSortedDistances()throws Exception{String b="[{\"type\":\"hospital\",\"lat\":\"37.502\",\"lon\":\"127\",\"name\":\"영문병원\",\"namedetails\":{\"name:ko\":\"테스트병원\"}},{\"type\":\"clinic\",\"lat\":37.5001,\"lon\":127,\"name\":\"테스트의원\"},{\"type\":\"road\",\"lat\":37.5,\"lon\":127,\"name\":\"병원로\"}]";List<NearbyPlaces.Place> p=MapLookupClient.places(b,"병원",37.5,127);assertEquals(2,p.size());assertEquals("테스트의원",p.get(0).name);assertEquals("테스트병원",p.get(1).name);assertTrue(p.get(0).distance<p.get(1).distance);}
    @Test public void invalidOrDistantResultsAreIgnored()throws Exception{assertTrue(MapLookupClient.places("[{\"type\":\"hospital\",\"lat\":90,\"lon\":180,\"name\":\"먼병원\"}]","병원",37.5,127).isEmpty());}
    @Test public void httpRequestIdentifiesTheAppAndUsesKorean()throws Exception{URL u=MapLookupClient.reverse(37.5,127);Connection c=new Connection(u,200,"{\"address\":{\"city\":\"서울\"}}");try(CloudRequest r=new MapLookupClient.Request(u,url->c,()->false)){assertTrue(r.execute().contains("서울"));}assertTrue(c.closed);assertEquals("ko",c.getRequestProperty("Accept-Language"));assertTrue(c.getRequestProperty("User-Agent").contains("github.com/Siroko-0516"));assertFalse(c.getInstanceFollowRedirects());}
    @Test public void rateLimitDoesNotRetryOrChangeProvider()throws Exception{int[] count={0};try(CloudRequest r=new MapLookupClient.Request(MapLookupClient.reverse(37.5,127),url->{count[0]++;return new Connection(url,429,"");},()->false)){assertTrue(assertThrows(IOException.class,r::execute).getMessage().contains("제한"));}assertEquals(1,count[0]);}
    @Test public void cancellationDoesNotSendCoordinates()throws Exception{int[] count={0};try(CloudRequest r=new MapLookupClient.Request(MapLookupClient.reverse(37.5,127),url->{count[0]++;return new Connection(url,200,"{}");},()->true)){assertThrows(IOException.class,r::execute);}assertEquals(0,count[0]);}
    @Test public void redirectsAndOversizedBodiesAreRejected()throws Exception{for(int code:new int[]{302,200}){Connection c=new Connection(MapLookupClient.reverse(37.5,127),code,"x".repeat(256*1024+1));try(CloudRequest r=new MapLookupClient.Request(c.getURL(),url->c,()->false)){assertThrows(IOException.class,r::execute);}assertTrue(c.closed);}}
    @Test public void unexpectedHostsCannotReceiveLocationOrCredentials(){assertThrows(IOException.class,()->new MapLookupClient.Request(new URL("https://example.com/reverse"),url->null,()->false));}
}
