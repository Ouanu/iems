package dev.ouanu.iems.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import dev.ouanu.iems.entity.DeviceToken;
import dev.ouanu.iems.entity.OperatorToken;
import dev.ouanu.iems.repository.AccessTokenBlacklistRepository;
import dev.ouanu.iems.repository.DeviceTokenRepository;
import dev.ouanu.iems.repository.OperatorTokenRepository;

@Service
public class TokenService {
    private final AccessTokenBlacklistRepository blacklistRepository;
    private final OperatorTokenRepository operatorTokenRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    public TokenService(AccessTokenBlacklistRepository blacklistRepository, OperatorTokenRepository operatorTokenRepository, DeviceTokenRepository deviceTokenRepository) {
        this.blacklistRepository = blacklistRepository;
        this.operatorTokenRepository = operatorTokenRepository;
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /**
     * Check if a token is blacklisted
     * @param jti
     * @return
     */
    public boolean isTokenBlacklisted(String jti) {
        return blacklistRepository.existsByJti(jti);
    }

    /**
     * Check if an operator token is valid
     * @param operatorId
     * @param token
     * @return
     */
    public boolean isOperatorTokenValid(Long operatorId, String refreshTokenHash) {
        Optional<OperatorToken> optional = operatorTokenRepository.findByOperatorIdAndRefreshTokenHash(operatorId, refreshTokenHash);
        if (optional.isEmpty()) {
            return false;
        } else {
            OperatorToken operatorToken = optional.get();
            return !operatorToken.isRevoked() && operatorToken.getExpiresAt().isAfter(java.time.Instant.now());
        }
    }

    /**
     * Check if a device token is valid
     * @param deviceId
     * @param token
     * @return
     */
    public boolean isDeviceTokenValid(Long deviceId, String refreshTokenHash) {
        Optional<DeviceToken> optional = deviceTokenRepository.findByDeviceIdAndRefreshTokenHash(deviceId, refreshTokenHash);
        if (optional.isEmpty()) {
            return false;
        } else {
            DeviceToken deviceToken = optional.get();
            return !deviceToken.isRevoked() && deviceToken.getExpiresAt().isAfter(java.time.Instant.now());
        }
    }
}
