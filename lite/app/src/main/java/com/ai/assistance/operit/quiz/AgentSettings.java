package com.ai.assistance.operit.quiz;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class AgentSettings {
    private static final String ALIAS = "operit_quiz_deepseek";
    private final SharedPreferences preferences;

    AgentSettings(Context context) {
        preferences = context.getSharedPreferences("quiz_agent", Context.MODE_PRIVATE);
    }

    boolean hasApiKey() { return preferences.contains("api_key"); }
    boolean isRunning() { return preferences.getBoolean("running", false); }
    void setRunning(boolean running) { preferences.edit().putBoolean("running", running).apply(); }

    void saveApiKey(String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        byte[] encrypted = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
        preferences.edit()
            .putString("api_key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("api_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .apply();
    }

    String apiKey() throws Exception {
        if (!hasApiKey()) throw new IllegalStateException("尚未配置 DeepSeek API Key");
        byte[] iv = Base64.decode(preferences.getString("api_iv", ""), Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(preferences.getString("api_key", ""), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
        return generator.generateKey();
    }
}
