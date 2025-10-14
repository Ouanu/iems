package dev.ouanu.iems.util;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

@Component
public final class JwtUtil {
    private final String secretKey;
    private final long accessExpiration; // in milliseconds
    private final long refreshExpiration; // in milliseconds

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtUtil(@Value("${jwt.secret}") String secretKey,
                   @Value("${jwt.access_expiration}") long accessExpiration,
                   @Value("${jwt.refresh_expiration}") long refreshExpiration) {
        this.secretKey = Objects.requireNonNull(secretKey, "Property 'jwt.secret' must be set");
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
        this.algorithm = Algorithm.HMAC256(this.secretKey);
        this.verifier = JWT.require(this.algorithm).build();
    }

    /**
     * Generate a JWT token for the given operator ID and token ID (jti).
     * If jti is null, a new UUID will be generated.
     * @param operatorId
     * @param jti
     * @return the generated JWT token
     */
    public String generateToken(Long operatorId, boolean isRefreshToken, String jti) {
        
        Instant now = Instant.now();
        Date iat = Date.from(now);
        Date exp = Date.from(now.plusMillis(isRefreshToken ? refreshExpiration : accessExpiration));
        return JWT.create()
                .withSubject(String.valueOf(operatorId))
                .withIssuedAt(iat)
                .withExpiresAt(exp)
                .withJWTId(jti)
                .sign(algorithm);
    }

    /**
     * Verify the given JWT token and return the decoded JWT.
     * @param token the JWT token to verify
     * @return the decoded JWT
     * @throws JWTVerificationException if the token is invalid or expired
     */
    public DecodedJWT verify(String token) throws JWTVerificationException {
        return verifier.verify(token);
    }

    /**
     * Validate the given JWT token.
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validate(String token) {
        try {
            verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    /**
     * Extract the JWT ID (jti) from the given token.
     * @param token the JWT token
     * @return the jti if present and valid, null otherwise
     */
    public String getJti(String token) {
        try {
            DecodedJWT jwt = verify(token);
            return jwt.getId();
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    /**
     * Extract the subject (sub) from the given token as a Long.
     * @param token the JWT token
     * @return the subject as Long if present and valid, null otherwise
     */
    public Long getSubjectAsLong(String token) {
        try {
            String sub = verify(token).getSubject();
            System.out.println("Token subject: " + sub);
            return sub == null ? null : Long.valueOf(sub);
        } catch (JWTVerificationException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extract the expiration date from the given token.
     * @param token the JWT token
     * @return the expiration date if present and valid, null otherwise
     */
    public Date getExpiration(String token) {
        try {
            return verify(token).getExpiresAt();
        } catch (JWTVerificationException e) {
            return null;
        }
    }

}
