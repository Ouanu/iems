package dev.ouanu.iems.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import dev.ouanu.iems.entity.DeviceToken;

@Repository
public interface DeviceTokenRepository extends MongoRepository<DeviceToken, String> {
    Optional<DeviceToken> findByRefreshTokenHashAndRevokedFalse(String refreshTokenHash);
    Optional<DeviceToken> findByDeviceIdAndRefreshTokenHash(Long deviceId, String refreshTokenHash);
    List<DeviceToken> findByDeviceIdAndRevokedFalse(Long deviceId);
    List<DeviceToken> findByExpiresAtBefore(Instant now);
}
