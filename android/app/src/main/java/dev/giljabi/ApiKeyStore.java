package dev.giljabi;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Personal API key is encrypted with an app-owned Android Keystore key, never a build secret. */
final class ApiKeyStore {
    private static final String ALIAS="giljabi_gemini_aes_v1",ENTRY="gemini_key_ciphertext";
    private final SharedPreferences preferences;
    ApiKeyStore(Context context){preferences=context.getSharedPreferences("cloud-key",Context.MODE_PRIVATE);}
    boolean hasKey(){return preferences.contains(ENTRY);}
    void save(String value) throws IOException {
        try{if(!preferences.edit().putString(ENTRY,encrypt(value,key())).commit())throw new IOException("API 키 저장에 실패했습니다.");}
        catch(GeneralSecurityException e){throw new IOException("API 키를 안전하게 저장하지 못했습니다.",e);}
    }
    String read() throws IOException {
        String value=preferences.getString(ENTRY,"");if(value.isEmpty())throw new IOException("Gemini 설정에서 개인 API 키를 입력하세요.");
        try{return decrypt(value,key());}catch(GeneralSecurityException|IllegalArgumentException e){throw new IOException("저장된 API 키를 읽지 못했습니다. 키를 삭제하고 다시 입력하세요.",e);}
    }
    void clear() throws IOException {if(!preferences.edit().remove(ENTRY).commit())throw new IOException("API 키 삭제에 실패했습니다.");}
    private SecretKey key() throws GeneralSecurityException,IOException {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
    static String encrypt(String value,SecretKey key) throws GeneralSecurityException {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));byte[] token=new byte[2+iv.length+encrypted.length];
        token[0]=1;token[1]=(byte)iv.length;System.arraycopy(iv,0,token,2,iv.length);System.arraycopy(encrypted,0,token,2+iv.length,encrypted.length);
        return Base64.getEncoder().encodeToString(token);
    }
    static String decrypt(String token,SecretKey key) throws GeneralSecurityException {
        byte[] data=Base64.getDecoder().decode(token);
        if(data.length<30||data[0]!=1||(data[1]&255)!=12)throw new GeneralSecurityException("Invalid encrypted key format");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,data,2,12));
        return new String(cipher.doFinal(data,14,data.length-14),StandardCharsets.UTF_8);
    }
}
