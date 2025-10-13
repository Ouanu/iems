package dev.ouanu.iems.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Document(collection = "device_tokens")
public class DeviceToken {
    @Id
    private String id;
    private Long deviceId;
    private String tokenId;
    private String refreshTokenHash;
    private Instant createdAt;
    private Instant lastUsedAt;
    @Indexed(name = "idx_expires_at")
    private Instant expiresAt;
    private boolean revoked;
}
