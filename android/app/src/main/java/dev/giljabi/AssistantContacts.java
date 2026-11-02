package dev.giljabi;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.*;

final class AssistantContacts {
    static final class Contact {final String name,number;Contact(String n,String p){name=n;number=p;}}
    static Contact find(Context context,String requested)throws IOException{
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)throw new IOException("앱에서 연락처 권한을 허용해주세요.");
        Map<String,Contact> matches=new LinkedHashMap<>();
        try(Cursor cursor=context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null)){
            int count=0;while(cursor!=null&&cursor.moveToNext()&&count++<20000){String name=cursor.getString(0),phone=AssistantCommands.phone(cursor.getString(1));if(!phone.isEmpty()&&AssistantCommands.sameName(name,requested))matches.put(phone,new Contact(name,phone));}
        }catch(SecurityException error){throw new IOException("연락처 권한을 확인해주세요.",error);}
        if(matches.isEmpty())throw new IOException("연락처에서 "+requested+" 이름의 일반 전화번호를 찾지 못했어요. 휴대폰 연락처의 저장 이름으로 말씀해주세요.");
        if(matches.size()!=1)throw new IOException(requested+" 연락처에 여러 번호가 있어요. 잘못 걸지 않도록 전화하지 않았어요. 연락처 이름을 구분해서 저장해주세요.");
        return matches.values().iterator().next();
    }
}
