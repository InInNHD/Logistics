package com.firefly.warehouse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
class CredentialCipher {
    static final String DEMO_KEY = "firefly-carrier-demo-key-change-in-production";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    CredentialCipher(@Value("${firefly.carrier.credential-key:}") String rawKey, Environment environment) {
        if (rawKey == null || rawKey.length() < 32) throw new IllegalStateException("CARRIER_CREDENTIAL_KEY must contain at least 32 characters");
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
        if (production && DEMO_KEY.equals(rawKey)) throw new IllegalStateException("Production requires a unique CARRIER_CREDENTIAL_KEY");
        try {
            key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize carrier credential encryption", e);
        }
    }

    String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encrypt carrier credential", e);
        }
    }

    String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot decrypt carrier credential", e);
        }
    }

    static String hint(String value) {
        int start = Math.max(value.length() - 4, 0);
        return "••••" + value.substring(start);
    }
}
