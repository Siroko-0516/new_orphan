package dev.giljabi;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Names/coordinates come from map records; a language model does not invent them. */
final class NearbyPlaces {
    static final class Place {
        final String name,phone;final double latitude,longitude,distance;
        Place(String n,String p,double lat,double lon,double d){name=n;phone=p;latitude=lat;longitude=lon;distance=d;}
        String url(){return "https://www.google.com/maps/search/?api=1&query="+latitude+","+longitude;}
    }
    static String query(String category,double lat,double lon)throws IOException{
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IOException("현재 위치를 확인하지 못했습니다.");
        String selector;
        switch(category){case "병원":selector="[\"amenity\"~\"^(hospital|clinic)$\"]";break;case "약국":selector="[\"amenity\"=\"pharmacy\"]";break;case "화장실":selector="[\"amenity\"=\"toilets\"]";break;case "편의점":selector="[\"shop\"=\"convenience\"]";break;case "카페":selector="[\"amenity\"=\"cafe\"]";break;case "음식점":selector="[\"amenity\"=\"restaurant\"]";break;default:throw new IOException("지원하는 장소 종류를 확인하세요.");}
        return "[out:json][timeout:15];nwr(around:3000,"+lat+","+lon+")"+selector+";out center tags;";
    }
    static List<Place> parse(String body,double lat,double lon)throws IOException{
        try{
            JSONArray elements=new JSONObject(body).getJSONArray("elements");List<Place> result=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(int i=0;i<Math.min(elements.length(),1500);i++){
                JSONObject item=elements.optJSONObject(i);if(item==null)continue;
                JSONObject tags=item.optJSONObject("tags");if(tags==null)continue;
                String name=tags.optString("name:ko",tags.optString("name","")).replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
                if(name.isEmpty()||name.length()>100)continue;
                JSONObject point=item.has("lat")?item:item.optJSONObject("center");if(point==null)continue;
                double y=point.optDouble("lat",Double.NaN),x=point.optDouble("lon",Double.NaN);
                if(!Double.isFinite(y)||!Double.isFinite(x)||Math.abs(y)>90||Math.abs(x)>180)continue;
                double distance=distance(lat,lon,y,x);if(distance>3200)continue;
                String identity=name+":"+Math.round(y*10000)+":"+Math.round(x*10000);if(!seen.add(identity))continue;
                result.add(new Place(name,AssistantCommands.phone(tags.optString("phone",tags.optString("contact:phone",""))),y,x,distance));
            }
            result.sort(Comparator.comparingDouble(p->p.distance));return new ArrayList<>(result.subList(0,Math.min(3,result.size())));
        }catch(JSONException error){throw new IOException("지도 검색 응답을 읽지 못했습니다.",error);}
    }
    static double distance(double a,double b,double c,double d){double p=Math.toRadians(c-a),q=Math.toRadians(d-b),h=Math.sin(p/2)*Math.sin(p/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(q/2)*Math.sin(q/2);return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));}
    static String spoken(String category,List<Place> places){
        if(places.isEmpty())return "지도 데이터에서 이름이 있는 주변 "+category+" 검색 결과를 찾지 못했어요. 주변에 없다는 뜻은 아니에요. 앱에서 지도 검색을 열어 확인할 수 있어요.";
        StringBuilder b=new StringBuilder("주변 "+category+" 검색 결과예요. ");
        for(int i=0;i<Math.min(2,places.size());i++){Place p=places.get(i);b.append(i+1).append("번, ").append(p.name).append(", 직선거리 약 ").append(p.distance<1000?Math.max(10,Math.round(p.distance/10)*10)+"미터":String.format(Locale.KOREAN,"%.1f킬로미터",p.distance/1000)).append(". ");}
        b.append("길을 따라 이동하는 거리와는 달라요.");return b.toString();
    }
}
