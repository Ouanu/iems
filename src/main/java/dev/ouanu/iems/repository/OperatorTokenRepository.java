package dev.ouanu.iems.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import dev.ouanu.iems.entity.OperatorToken;

@Repository
public interface OperatorTokenRepository extends MongoRepository<OperatorToken, String> {
    Optional<OperatorToken> findByRefreshTokenHashAndRevokedFalse(String refreshTokenHash);
    List<OperatorToken> findByOperatorIdAndRevokedFalse(Long operatorId);
    List<OperatorToken> findByExpiresAtBefore(Instant now);
    Optional<OperatorToken> findByOperatorIdAndRefreshTokenHash(Long operatorId, String refreshTokenHash);

}
