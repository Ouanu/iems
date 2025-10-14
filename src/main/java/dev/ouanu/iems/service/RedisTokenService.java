package dev.ouanu.iems.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisTokenService {
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisTokenService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklistAccessToken(String jti, Instant expiresAt) {
        String key = "blacklist:access:" + jti;
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(key, "1", ttl);
        } else {
            redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(5));
        }
    }

    public boolean isTokenBlacklisted(String jti) {
        String key = "blacklist:access:" + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void storeRefreshToken(String refreshHash, String ownerMarker, Instant expiresAt) {
        String key = "refresh:" + refreshHash;
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(key, ownerMarker, ttl);
        } else {
            redisTemplate.opsForValue().set(key, ownerMarker, Duration.ofMinutes(5));
        }
    }

    public boolean isRefreshTokenStored(String refreshHash) {
        String key = "refresh:" + refreshHash;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void revokeRefreshToken(String refreshHash) {
        String key = "refresh:" + refreshHash;
        redisTemplate.delete(key);
    }

}
