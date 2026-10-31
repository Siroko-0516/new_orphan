package dev.giljabi;

import java.security.GeneralSecurityException;
import java.util.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApiKeyStoreTest {
    private final SecretKey key=new SecretKeySpec(new byte[32],"AES");
    @Test public void encryptsAndUsesDifferentNonces()throws Exception {
        String secret="test-personal-api-key-not-real";
        String first=ApiKeyStore.encrypt(secret,key),second=ApiKeyStore.encrypt(secret,key);
        assertNotEquals(first,second);assertFalse(first.contains(secret));assertEquals(secret,ApiKeyStore.decrypt(first,key));assertEquals(secret,ApiKeyStore.decrypt(second,key));
    }
    @Test public void tamperingCannotProduceAnApiKey()throws Exception {
        byte[] token=Base64.getDecoder().decode(ApiKeyStore.encrypt("personal-key",key));token[token.length-1]^=1;
        assertThrows(GeneralSecurityException.class,()->ApiKeyStore.decrypt(Base64.getEncoder().encodeToString(token),key));
    }
    @Test public void anotherKeyCannotDecrypt()throws Exception {
        String token=ApiKeyStore.encrypt("personal-key",key);byte[] other=new byte[32];Arrays.fill(other,(byte)1);
        assertThrows(GeneralSecurityException.class,()->ApiKeyStore.decrypt(token,new SecretKeySpec(other,"AES")));
    }
    @Test public void rejectsInvalidTokenFormat() {
        assertThrows(GeneralSecurityException.class,()->ApiKeyStore.decrypt(Base64.getEncoder().encodeToString(new byte[29]),key));
        assertThrows(IllegalArgumentException.class,()->ApiKeyStore.decrypt("not-base64!",key));
    }
}
