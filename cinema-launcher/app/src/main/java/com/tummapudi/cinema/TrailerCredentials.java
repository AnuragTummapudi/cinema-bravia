package com.tummapudi.cinema;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** The owner provisions the key at runtime. No credential is part of the APK. */
final class TrailerCredentials {
    private static final String ALIAS="cinema.youtube.v1";
    static synchronized void save(Context context,String value)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());generator.generateKey();
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,store.getKey(ALIAS,null));
        String encrypted=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!context.getSharedPreferences("trailer_credentials",0).edit().putString("encryptedKey",encrypted).putBoolean("consent",true).commit())throw new java.io.IOException("Credential storage unavailable");
    }
    static synchronized String read(Context context)throws Exception{
        String encoded=context.getSharedPreferences("trailer_credentials",0).getString("encryptedKey","");if(encoded.isEmpty())return "";
        String[] parts=encoded.split(":",2);if(parts.length!=2)throw new java.security.GeneralSecurityException("Credential format invalid");
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,store.getKey(ALIAS,null),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    static synchronized void remove(Context context)throws Exception{
        context.getSharedPreferences("trailer_credentials",0).edit().clear().commit();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);
    }
}
