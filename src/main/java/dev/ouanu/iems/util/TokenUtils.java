package dev.ouanu.iems.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public final class TokenUtils {
    
    

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String randomRefreshToken() {
        byte[] b = new byte[64];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String randomTokenId() {
        return UUID.randomUUID().toString();
    }

    public static String sha256Hex(String value) throws IllegalArgumentException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte by : digest) sb.append(String.format("%02x", by));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Failed to compute SHA-256 hash", e);
        }
    }

}
