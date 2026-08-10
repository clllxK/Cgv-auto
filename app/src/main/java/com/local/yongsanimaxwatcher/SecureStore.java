package com.local.yongsanimaxwatcher;

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

public final class SecureStore {
    private static final String PREF = "secure_store";
    private static final String ALIAS = "yongsan_imax_watcher_key";
    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        ensureKey();
    }

    private void ensureKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                kg.init(new KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());
                kg.generateKey();
            }
        } catch (Exception e) {
            throw new IllegalStateException("보안 저장소 초기화 실패", e);
        }
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    public void put(String name, String value) {
        try {
            if (value == null || value.isEmpty()) {
                prefs.edit().remove(name).apply();
                return;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString(name, packed).apply();
        } catch (Exception e) {
            throw new IllegalStateException("보안 값 저장 실패", e);
        }
    }

    public String get(String name) {
        try {
            String packed = prefs.getString(name, "");
            if (packed == null || packed.isEmpty()) return "";
            String[] parts = packed.split(":", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public void putLong(String name, long value) {
        put(name, Long.toString(value));
    }

    public long getLong(String name, long def) {
        try {
            String v = get(name);
            return v.isEmpty() ? def : Long.parseLong(v);
        } catch (Exception e) {
            return def;
        }
    }

    public void clearTokens() {
        prefs.edit()
                .remove("access_token")
                .remove("refresh_token")
                .remove("access_expires_at")
                .apply();
    }
}
