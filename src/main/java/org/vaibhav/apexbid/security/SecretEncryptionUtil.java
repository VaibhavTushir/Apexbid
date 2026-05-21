package org.vaibhav.apexbid.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class SecretEncryptionUtil {
    @Value("${PRODUCT_SECRET_ENCRYPTION_KEY}")
    private String secretEncryptionKey;

    private SecretKeySpec getDecodedKey(){
        byte[] decodedKey= Base64.getDecoder().decode(secretEncryptionKey);
        return new SecretKeySpec(decodedKey, "AES");
    }
    public String encrypt(String plainText){
        if(plainText==null || plainText.isEmpty()){
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, getDecodedKey());
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    public String decrypt(String cipherText){
        if(cipherText==null || cipherText.isEmpty()){
            return "";
        }
        try{
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, getDecodedKey());
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)));

        }catch (Exception e){
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
