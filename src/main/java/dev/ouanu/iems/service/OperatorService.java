package dev.ouanu.iems.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.auth0.jwt.exceptions.JWTVerificationException;

import dev.ouanu.iems.constant.BizType;
import dev.ouanu.iems.dto.AdminResetPasswordDTO;
import dev.ouanu.iems.dto.ChangePasswordDTO;
import dev.ouanu.iems.dto.OperatorLogoutDTO;
import dev.ouanu.iems.dto.RegisterOperatorDTO;
import dev.ouanu.iems.dto.UpdateOperatorDTO;
import dev.ouanu.iems.entity.AccessTokenBlacklist;
import dev.ouanu.iems.entity.Operator;
import dev.ouanu.iems.entity.OperatorToken;
import dev.ouanu.iems.mapper.OperatorMapper;
import dev.ouanu.iems.repository.AccessTokenBlacklistRepository;
import dev.ouanu.iems.repository.OperatorTokenRepository;
import dev.ouanu.iems.util.JwtUtil;
import dev.ouanu.iems.util.TokenUtils;
import dev.ouanu.iems.vo.OperatorVO;
import dev.ouanu.iems.vo.TokenVO;

@Service
public class OperatorService {

    private final OperatorMapper operatorMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OperatorTokenRepository operatorTokenRepository;
    private final AccessTokenBlacklistRepository blacklistRepository;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private final SnowflakeIdService snowflakeIdService;
    private final RedisTokenService redisTokenService;

    public OperatorService(OperatorMapper operatorMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           OperatorTokenRepository operatorTokenRepository,
                           AccessTokenBlacklistRepository blacklistRepository,
                           SnowflakeIdService snowflakeIdService,
                           RedisTokenService redisTokenService) {
        this.snowflakeIdService = snowflakeIdService;
        this.operatorMapper = operatorMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.operatorTokenRepository = operatorTokenRepository;
        this.blacklistRepository = blacklistRepository;
        this.redisTokenService = redisTokenService;
    }

    /**
     * Create a new operator by super operator
     * @param dto
     * @return
     */
    @Transactional
    @CacheEvict(value = {"operators:list","operators:byId"}, allEntries = true)
    public Operator createOperator(RegisterOperatorDTO dto) {
        if (operatorMapper.existsByPhone(dto.getPhone())) {
            throw new IllegalStateException("Phone already in use");
        }
        if (operatorMapper.existsByEmail(dto.getEmail())) {
            throw new IllegalStateException("Email already in use");
        }
        Operator operator = dto.toEntity(passwordEncoder);
        operator.setId(snowflakeIdService.nextIdAndPersist(BizType.OPERATOR));
        operator.setUuid(UUID.randomUUID().toString());
        int ret = operatorMapper.insert(operator);
        if (ret != 1) {
            throw new IllegalStateException("Failed to create operator");
        }
        return operator;
    }

    /**
     * Operator login with phone and password
     * @param phone
     * @param password
     * @return
     */
    public ResponseEntity<TokenVO> login(String phone, String password) {
        Operator operator = operatorMapper.selectByPhone(phone);
        if (operator == null || !passwordEncoder.matches(password, operator.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String accessToken = jwtUtil.generateToken(operator.getId(), /*isRefresh*/ false, accessJti);
        String refreshToken = jwtUtil.generateToken(operator.getId(), /*isRefresh*/ true, refreshJti);

        // 保存 refresh 的元数据到 DB/Mongo（只存 refreshHash + refreshJti）
        OperatorToken token = new OperatorToken();
        token.setOperatorId(operator.getId());
        token.setTokenId(refreshJti); // refresh jti
        token.setRefreshTokenHash(TokenUtils.sha256Hex(refreshToken));
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(jwtUtil.getExpiration(refreshToken).toInstant());
        operatorTokenRepository.save(token);

        // 同步写入 Redis（便于快速校验）
        redisTokenService.storeRefreshToken(TokenUtils.sha256Hex(refreshToken), "operator:" + operator.getId(), token.getExpiresAt());

        // 返回 access + refresh 给客户端
        var tokenVO = new TokenVO(accessToken, refreshToken, jwtUtil.getExpiration(accessToken));
        return ResponseEntity.ok(tokenVO);
    }

    /**
     * Refresh access token using refresh token
     * @param refreshToken the refresh token
     * @return the new access token and refresh token
     */
    public ResponseEntity<TokenVO> refreshToken(String refreshToken) {
        var optional = operatorTokenRepository.findByRefreshTokenHashAndRevokedFalse(TokenUtils.sha256Hex(refreshToken));
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        var token = optional.get();

        // check if token is revoked
        if (token.isRevoked()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        // check if token is expired
        if (token.getExpiresAt().isBefore(Instant.now())) {
            // revoke the token
            token.setRevoked(true);
            operatorTokenRepository.save(token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

         // 验证 refresh token 签名/完整性（若 jwtUtil.verify 抛异常则拒绝）
        try {
            jwtUtil.verify(refreshToken);
        } catch (JWTVerificationException ex) {
            token.setRevoked(true);
            operatorTokenRepository.save(token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        // 生成新的 access token（每次都生成）
        String accessJti = UUID.randomUUID().toString();
        String newAccessToken = jwtUtil.generateToken(token.getOperatorId(), false, accessJti);

        // 如果距离 refresh 到期 48 小时内 -> rotate（撤销旧记录并新建）
        if (token.getExpiresAt().isBefore(Instant.now().plus(Duration.ofHours(48)))) {
            // 撤销旧 refresh 记录（便于审计与重放检测）
            token.setRevoked(true);
            operatorTokenRepository.save(token);

            // 生成并保存新的 refresh token 记录
            String newJti = UUID.randomUUID().toString();
            String newRefreshToken = jwtUtil.generateToken(token.getOperatorId(), true, newJti);
            
            OperatorToken newToken = new OperatorToken();
            newToken.setOperatorId(token.getOperatorId());
            newToken.setTokenId(newJti);
            newToken.setRefreshTokenHash(TokenUtils.sha256Hex(newRefreshToken));
            newToken.setCreatedAt(Instant.now());
            newToken.setLastUsedAt(Instant.now());
            newToken.setExpiresAt(jwtUtil.getExpiration(newRefreshToken).toInstant());
            newToken.setRevoked(false);
            operatorTokenRepository.save(newToken);

            TokenVO tokenVO = new TokenVO(newAccessToken, newRefreshToken, jwtUtil.getExpiration(newAccessToken));
            return ResponseEntity.ok(tokenVO);
        } else {
            // 不旋转：更新 lastUsed 并返回新的 access（refresh 不变）
            token.setLastUsedAt(Instant.now());
            operatorTokenRepository.save(token);
            TokenVO tokenVO = new TokenVO(newAccessToken, refreshToken, jwtUtil.getExpiration(newAccessToken));
            return ResponseEntity.ok(tokenVO);
        }
    }

    /**
     * Logout the operator by blacklisting the access token and revoking the refresh token
     * @param accessToken the access token
     * @param refreshToken  the refresh token
     * @return 
     */
    public ResponseEntity<String> logout(OperatorLogoutDTO dto) {
        var decoded = jwtUtil.verify(dto.getAccessToken());
        var jti = decoded.getId();
        var exp = decoded.getExpiresAt().toInstant();

        // DB 记录
        AccessTokenBlacklist blacklist = new AccessTokenBlacklist();
        blacklist.setJti(jti);
        blacklist.setExpiresAt(exp);
        blacklist.setReason("Operator logout");
        blacklistRepository.save(blacklist);

        // Redis 黑名单
        redisTokenService.blacklistAccessToken(jti, exp);

        // 撤销 refresh（DB + Redis）
        var optional = operatorTokenRepository.findByRefreshTokenHashAndRevokedFalse(TokenUtils.sha256Hex(dto.getRefreshToken()));
        if (optional.isPresent()) {
            var token = optional.get();
            token.setRevoked(true);
            operatorTokenRepository.save(token);
        }
        redisTokenService.revokeRefreshToken(TokenUtils.sha256Hex(dto.getRefreshToken()));
        return ResponseEntity.ok("Logged out successfully");
    }

    /**
     * Revoke a refresh token (e.g. for admin to force logout)
     * @param refreshToken the refresh token
     * @return
     */
    public ResponseEntity<String> revokeRefreshToken(String refreshToken) {
        var optional = operatorTokenRepository.findByRefreshTokenHashAndRevokedFalse(TokenUtils.sha256Hex(refreshToken));
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Refresh token not found or already revoked");
        }
        var token = optional.get();
        token.setRevoked(true);
        operatorTokenRepository.save(token);
        return ResponseEntity.ok("Refresh token revoked successfully");
    }

    /**
     * Revoke an access token (e.g. for admin to force logout)
     * @param accessToken the access token
     * @return
     */
    public ResponseEntity<String> revokeAccessToken(String accessToken) {
        var decoded = jwtUtil.verify(accessToken);
        var jti = decoded.getId();
        var exp = decoded.getExpiresAt().toInstant();
        AccessTokenBlacklist blacklist = new AccessTokenBlacklist();
        blacklist.setJti(jti);
        blacklist.setExpiresAt(exp);
        blacklist.setReason("Admin revoked access token");
        blacklistRepository.save(blacklist);
        return ResponseEntity.ok("Access token revoked successfully");
    }
    
    /**
     * Change password for the authenticated operator
     * @param dto the change password DTO
     * @return
     */
    @Transactional
    public ResponseEntity<String> changePassword(ChangePasswordDTO dto) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        String phone = auth.getName();
        Operator operator = operatorMapper.selectByPhone(phone);
        if (operator == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Operator unfounded");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("New password and confirm password do not match");
        }
        if (!passwordEncoder.matches(dto.getCurrentPassword(), operator.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Old password is incorrect");
        }
        operator.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        int ret = operatorMapper.update(operator);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to change password");
        }
        return ResponseEntity.ok("Password changed successfully");
    }

    /**
     * Admin reset password for an operator
     * @param dto the reset password DTO
     * @return 
     */
    @Transactional
    @CacheEvict(value = "operators:byId", key = "#dto.id")
    public void adminResetPassword(AdminResetPasswordDTO dto) {
        // verify auth's permission
        Operator operator = operatorMapper.selectById(dto.getId());
        if (operator == null) {
            throw new IllegalArgumentException("Operator not found");
        }
        operator.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        int ret = operatorMapper.update(operator);
        if (ret != 1) {
            throw new IllegalStateException("Failed to reset password");
        }
    }

    /**
     * Admin update operator profile
     * @param id the operator ID
     * @param dto the operator DTO
     * @return 
     */
    @Transactional
    @CacheEvict(value = {"operators:list", "operators:byId"}, key = "#id")
    public Operator adminUpdateProfile(Long id, UpdateOperatorDTO dto) {
        // verify auth's permission
        Operator operator = operatorMapper.selectById(id);
        if (operator == null) {
            throw new IllegalArgumentException("Operator not found");
        }
        updateOperatorFields(dto, operator);
        int ret = operatorMapper.update(operator);
        if (ret != 1) {
            throw new IllegalStateException("Failed to update profile");
        }
        return operator;
    }

    /**
     * Operator update own profile
     * @param dto the operator DTO
     * @return
     */
    @Transactional
    @CacheEvict(value = {"operators:list", "operators:byId"}, key = "#result.id")
    public OperatorVO updateProfile(UpdateOperatorDTO dto) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new SecurityException("Unauthorized");
        }
        String phone = auth.getName();
        Operator operator = operatorMapper.selectByPhone(phone);
        if (operator == null) {
            throw new IllegalArgumentException("Operator not found");
        }
        updateOperatorFields(dto, operator);
    
        int ret = operatorMapper.update(operator);
        if (ret != 1) {
            throw new IllegalStateException("Failed to update profile");
        }
        return OperatorVO.fromEntity(operator);
    }

    private void updateOperatorFields(UpdateOperatorDTO dto, Operator operator) {
        operator.setDisplayName(dto.getDisplayName() != null ? dto.getDisplayName() : operator.getDisplayName());
        operator.setPhone(dto.getPhone() != null ? dto.getPhone() : operator.getPhone());
        operator.setEmail(dto.getEmail() != null ? dto.getEmail() : operator.getEmail());
        operator.setAccountType(dto.getAccountType() != null ? dto.getAccountType() : operator.getAccountType());
        operator.setDepartment(dto.getDepartment() != null ? dto.getDepartment() : operator.getDepartment());
        operator.setTeam(dto.getTeam() != null ? dto.getTeam() : operator.getTeam());
        operator.setPosition(dto.getPosition() != null ? dto.getPosition() : operator.getPosition());
        operator.setLevel(dto.getLevel() != null ? dto.getLevel() : operator.getLevel());
        if (dto.getActive() != null) {
            operator.setActive(dto.getActive());
        }
    }

    /**
     * List operators with pagination
     * @param offset the offset
     * @param limit the limit
     * @return
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "operators:list", key = "#offset + ':' + #limit")
    public List<OperatorVO> listOperators(int offset, int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        if (offset < 0) {
            return Collections.emptyList(); // 或者抛出异常
        }
        List<Operator> operators = operatorMapper.list(offset, limit);
        return operators.stream().map(OperatorVO::fromEntity).toList();
    }

    /**
     * Query operators with parameters
     * @param params the query parameters, e.g. department, team, position, level, active
     * @return 
     */
    @Transactional(readOnly = true)
    public List<OperatorVO> query(Map<String, Object> params) {
        String offsetKey = "offset";
        String limitKey = "limit";
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("Query params cannot be empty");
        }
        if (!params.containsKey(offsetKey)) {
            throw new IllegalArgumentException("Query params must contain offset");
        } else {
            int offset = (int) params.get(offsetKey);
            if (offset < 0) {
                throw new IllegalArgumentException("Offset must be non-negative");
            }
        }
        if (!params.containsKey(limitKey)) {
            throw new IllegalArgumentException("Query params must contain limit");
        } else {
            int limit = (int) params.get(limitKey);
            if (limit <= 0) {
                params.put(limitKey, DEFAULT_LIMIT);
            } else if (limit > MAX_LIMIT) {
                params.put(limitKey, MAX_LIMIT);
            }
        }
        List<Operator> operators = operatorMapper.query(params);
        return operators.stream().map(OperatorVO::fromEntity).toList();
    }

    /**
     * Delete an operator by ID
     * @param id the operator ID
     * @return
     */
    @Transactional
    @CacheEvict(value = {"operators:list", "operators:byId"}, key = "#id")
    public void delete(Long id) {
        // verify auth's permission
        Operator operator = operatorMapper.selectById(id);
        if (operator == null) {
            throw new IllegalArgumentException("Operator not found");
        }
        int ret = operatorMapper.deleteById(id);
        if (ret != 1) {
            throw new IllegalStateException("Failed to delete operator");
        }
    }

    /**
     * Get operator by ID
     * @param id the operator ID
     * @return
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "operators:byId", key = "#id")
    public OperatorVO getOperator(Long id) {
        Operator operator = operatorMapper.selectById(id);
        if (operator == null) {
            return null;
        }
        return OperatorVO.fromEntity(operator);
    }
}
