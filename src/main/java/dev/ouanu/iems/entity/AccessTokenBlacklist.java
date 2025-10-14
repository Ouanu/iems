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
@Document(collection = "access_token_blacklist")
public class AccessTokenBlacklist implements Serializable{
    @Id
    private String id;

    private String jti; // JWT ID

    private String reason;
    private String operatorIp;
    private String operatorAgent;

    private Instant createdAt;

    @Indexed(name = "idx_blacklist_expires_at")
    private Instant expiresAt;

}
