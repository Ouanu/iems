package dev.ouanu.iems.entity;

import java.io.Serializable;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "operator_tokens")
public class OperatorToken implements Serializable{
    // 改为 String（Mongo 文档 id），由 Mongo 或应用生成 UUID
    @Id
    private String id;

    private Long operatorId;
    private String tokenId;
    private String refreshTokenHash;
    private Instant createdAt;
    private Instant lastUsedAt;
    @Indexed(name = "idx_expires_at")
    private Instant expiresAt;
    private boolean revoked;
}
