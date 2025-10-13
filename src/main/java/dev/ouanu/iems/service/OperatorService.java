package dev.ouanu.iems.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public OperatorService(OperatorMapper operatorMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           OperatorTokenRepository operatorTokenRepository,
                           AccessTokenBlacklistRepository blacklistRepository,
                           SnowflakeIdService snowflakeIdService) {
        this.snowflakeIdService = snowflakeIdService;
        this.operatorMapper = operatorMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.operatorTokenRepository = operatorTokenRepository;
        this.blacklistRepository = blacklistRepository;
    }

    /**
     * Create a new operator by super operator
     * @param dto
     * @return
     */
    @Transactional
    public ResponseEntity<String> createOperator(RegisterOperatorDTO dto) {
        // verify auth's permission
        if (operatorMapper.existsByPhone(dto.getPhone())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Phone already in use");
        }
        if (operatorMapper.existsByEmail(dto.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already in use");
        }
        Operator operator = dto.toEntity(passwordEncoder);
        operator.setId(snowflakeIdService.nextIdAndPersist(BizType.OPERATOR));
        operator.setUuid(UUID.randomUUID().toString());
        int ret = operatorMapper.insert(operator);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create operator");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Operator created with ID: " + operator.getId());
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
        // blacklist access token
        var decoded = jwtUtil.verify(dto.getAccessToken());
        var jti = decoded.getId();
        var exp = decoded.getExpiresAt().toInstant();
        AccessTokenBlacklist blacklist = new AccessTokenBlacklist();
        blacklist.setJti(jti);
        blacklist.setExpiresAt(exp);
        blacklist.setReason("Operator logout");
        blacklistRepository.save(blacklist);

        // revoke refresh token
        var optional = operatorTokenRepository.findByRefreshTokenHashAndRevokedFalse(TokenUtils.sha256Hex(dto.getRefreshToken()));
        if (optional.isPresent()) {
            var token = optional.get();
            token.setRevoked(true);
            operatorTokenRepository.save(token);
        }
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
    public ResponseEntity<String> adminResetPassword(AdminResetPasswordDTO dto) {
        // verify auth's permission
        Operator operator = operatorMapper.selectById(dto.getId());
        if (operator == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        operator.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        int ret = operatorMapper.update(operator);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to reset password");
        }
        return ResponseEntity.ok("Password reset successfully");
    }

    /**
     * Admin update operator profile
     * @param id the operator ID
     * @param dto the operator DTO
     * @return 
     */
    @Transactional
    public ResponseEntity<String> adminUpdateProfile(Long id, UpdateOperatorDTO dto) {
        // verify auth's permission
        Operator operator = operatorMapper.selectById(id);
        if (operator == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Operator not found");
        }
        operator.setDisplayName(dto.getDisplayName());
        operator.setPhone(dto.getPhone());
        operator.setEmail(dto.getEmail());
        operator.setAccountType(dto.getAccountType());
        operator.setDepartment(dto.getDepartment());
        operator.setTeam(dto.getTeam());
        operator.setPosition(dto.getPosition());
        operator.setLevel(dto.getLevel());
        operator.setActive(dto.getActive());
        int ret = operatorMapper.update(operator);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update profile");
        }
        return ResponseEntity.ok("Profile updated successfully");
    }

    /**
     * Operator update own profile
     * @param dto the operator DTO
     * @return
     */
    @Transactional
    public ResponseEntity<OperatorVO> updateProfile(UpdateOperatorDTO dto) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String phone = auth.getName();
        Operator operator = operatorMapper.selectByPhone(phone);
        if (operator == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        operator.setDisplayName(dto.getDisplayName());
        operator.setPhone(dto.getPhone());
        operator.setEmail(dto.getEmail());
        operator.setAccountType(dto.getAccountType());
        operator.setDepartment(dto.getDepartment());
        operator.setTeam(dto.getTeam());
        operator.setPosition(dto.getPosition());
        operator.setLevel(dto.getLevel());
        if (dto.getActive() != null) {
            operator.setActive(dto.getActive());
        }
        int ret = operatorMapper.update(operator);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        return ResponseEntity.ok(OperatorVO.fromEntity(operator));
    }

    /**
     * List operators with pagination
     * @param offset the offset
     * @param limit the limit
     * @return
     */
    @Transactional(readOnly = true)
    public ResponseEntity<List<OperatorVO>> listOperators(int offset, int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        if (offset < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        List<Operator> operators = operatorMapper.list(offset, limit);
        List<OperatorVO> operatorVOs = operators.stream().map(OperatorVO::fromEntity).toList();
        return ResponseEntity.ok(operatorVOs);
    }

    /**
     * Query operators with parameters
     * @param params the query parameters, e.g. department, team, position, level, active
     * @return 
     */
    @Transactional(readOnly = true)
    public ResponseEntity<List<OperatorVO>> query(Map<String, Object> params) {
        String offsetKey = "offset";
        String limitKey = "limit";
        if (params == null || params.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!params.containsKey(offsetKey)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } else {
            int offset = (int) params.get(offsetKey);
            if (offset < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }
        if (!params.containsKey(limitKey)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } else {
            int limit = (int) params.get(limitKey);
            if (limit <= 0) {
                params.put(limitKey, DEFAULT_LIMIT);
            } else if (limit > MAX_LIMIT) {
                params.put(limitKey, MAX_LIMIT);
            }
        }
        List<Operator> operators = operatorMapper.query(params);
        List<OperatorVO> operatorVOs = operators.stream().map(OperatorVO::fromEntity).toList();
        return ResponseEntity.ok(operatorVOs);
    }

    /**
     * Delete an operator by ID
     * @param id the operator ID
     * @return
     */
    public ResponseEntity<String> delete(Long id) {
        // verify auth's permission
        Operator operator = operatorMapper.selectById(id);
        if (operator == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Operator not found");
        }
        int ret = operatorMapper.deleteById(id);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete operator");
        }
        return ResponseEntity.ok("Operator deleted successfully");
    }

    /**
     * Get operator by ID
     * @param id the operator ID
     * @return
     */
    public ResponseEntity<OperatorVO> getOperator(Long id) {
        Operator operator = operatorMapper.selectById(id);
        if (operator == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(OperatorVO.fromEntity(operator));
    }
}
