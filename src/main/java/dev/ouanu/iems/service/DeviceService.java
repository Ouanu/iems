package dev.ouanu.iems.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.auth0.jwt.exceptions.JWTVerificationException;

import dev.ouanu.iems.constant.BizType;
import dev.ouanu.iems.dto.DeviceLogoutDTO;
import dev.ouanu.iems.dto.RegisterDeviceDTO;
import dev.ouanu.iems.dto.UpdateDeviceDTO;
import dev.ouanu.iems.entity.Device;
import dev.ouanu.iems.entity.DeviceToken;
import dev.ouanu.iems.mapper.DeviceMapper;
import dev.ouanu.iems.repository.AccessTokenBlacklistRepository;
import dev.ouanu.iems.repository.DeviceTokenRepository;
import dev.ouanu.iems.util.JwtUtil;
import dev.ouanu.iems.util.TokenUtils;
import dev.ouanu.iems.vo.DeviceVO;

@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final JwtUtil jwtUtil;
    private final DeviceTokenRepository deviceTokenRepository;
    private final AccessTokenBlacklistRepository blacklistRepository;
    private final SnowflakeIdService snowflakeIdService;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    public DeviceService(DeviceMapper deviceMapper,
                         JwtUtil jwtUtil,
                         DeviceTokenRepository deviceTokenRepository,
                         AccessTokenBlacklistRepository blacklistRepository, SnowflakeIdService snowflakeIdService) {
        this.deviceMapper = deviceMapper;
        this.jwtUtil = jwtUtil;
        this.deviceTokenRepository = deviceTokenRepository;
        this.blacklistRepository = blacklistRepository;
        this.snowflakeIdService = snowflakeIdService;
    }

    @Transactional
    public ResponseEntity<String> registerDevice(RegisterDeviceDTO dto) {
        var dm = deviceMapper.selectByMacAddress(dto.getMacAddress());
        if (dm != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Device with this MAC address already exists");
        }
        Device device = RegisterDeviceDTO.toEntity(dto);
        device.setId(snowflakeIdService.nextIdAndPersist(BizType.DEVICE));
        device.setUuid(UUID.randomUUID().toString());
        // default values if null
        if (device.getActive() == null) device.setActive(true);
        if (device.getLocked() == null) device.setLocked(false);
        int ret = deviceMapper.insert(device);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create device");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Device created with ID: " + device.getId());
    }

    @Transactional
    public ResponseEntity<String> updateDevice(Long id, UpdateDeviceDTO dto) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not found");
        }
        device.setActive(dto.getActive());
        device.setLocked(dto.getLocked());
        device.setModel(dto.getModel());
        device.setBrand(dto.getBrand());
        device.setSerialno(dto.getSerialno());
        device.setAndroidVersion(dto.getAndroidVersion());
        device.setAppVersion(dto.getAppVersion());
        device.setRomVersion(dto.getRomVersion());
        int ret = deviceMapper.update(device);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update device");
        }
        return ResponseEntity.ok("Device updated successfully");
    }

    @Transactional
    public ResponseEntity<String> updateMyProfile(UpdateDeviceDTO dto) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof Long)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Long deviceId = (Long) auth.getPrincipal();
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not found");
        }
        if (!device.getActive() || device.getLocked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Device is inactive or locked");    
        }
        device.setModel(dto.getModel());
        device.setBrand(dto.getBrand());
        device.setSerialno(dto.getSerialno());
        device.setAndroidVersion(dto.getAndroidVersion());
        device.setAppVersion(dto.getAppVersion());
        device.setRomVersion(dto.getRomVersion());
        int ret = deviceMapper.update(device);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update device");
        }
        return ResponseEntity.ok("Device updated successfully");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<DeviceVO> getDeviceById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(DeviceVO.fromEntity(device));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<DeviceVO> getDeviceByUuid(String uuid) {
        Device device = deviceMapper.selectByUuid(uuid);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(DeviceVO.fromEntity(device));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<List<DeviceVO>> listDevices(int offset, int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        if (offset < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        List<Device> devices = deviceMapper.list(offset, limit);
        
        List<DeviceVO> voList = devices.stream().map(DeviceVO::fromEntity).toList();
        return ResponseEntity.ok(voList);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<List<DeviceVO>> queryDevices(Map<String, Object> params) {
        String offsetKey = "offset";
        String limitKey = "limit";
        if (params == null || params.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        // validate offset/limit if provided
        if (params.containsKey(offsetKey)) {
            int offset = (int) params.get(offsetKey);
            if (offset < 0) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (params.containsKey(limitKey)) {
            int limit = (int) params.get(limitKey);
            if (limit <= 0) params.put(limitKey, DEFAULT_LIMIT);
            else if (limit > MAX_LIMIT) params.put(limitKey, MAX_LIMIT);
        }
        List<Device> devices = deviceMapper.query(params);
        List<DeviceVO> voList = devices.stream().map(DeviceVO::fromEntity).toList();
        return ResponseEntity.ok(voList);
    }

    @Transactional
    public ResponseEntity<String> deleteDevice(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not found");
        }
        int ret = deviceMapper.deleteById(id);
        if (ret != 1) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete device");
        }
        return ResponseEntity.ok("Device deleted successfully");
    }

    // --- Authentication / token management for devices ---

    public static class LoginRequest {
        public final String uuid;
        public final String signatureHash;

        public LoginRequest(String uuid, String signatureHash) {
            this.uuid = uuid;
            this.signatureHash = signatureHash;
        }
    }

    public static class RefreshRequest {
        public final String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public ResponseEntity<dev.ouanu.iems.vo.TokenVO> login(String macAddress, String signatureHash) {
        Device device = deviceMapper.selectByMacAddress(macAddress);
        if (device == null || device.getSignatureHash() == null || !device.getSignatureHash().equals(signatureHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String accessToken = jwtUtil.generateToken(device.getId(), /*isRefresh*/ false, accessJti);
        String refreshToken = jwtUtil.generateToken(device.getId(), /*isRefresh*/ true, refreshJti);

        DeviceToken token = new DeviceToken();
        token.setDeviceId(device.getId());
        token.setTokenId(refreshJti); // Note: DeviceToken has no tokenId field in entity — use refreshTokenHash only.
        // But entity fields: id, deviceId, refreshTokenHash, createdAt, lastUsedAt, expiresAt, revoked
        token.setRefreshTokenHash(TokenUtils.sha256Hex(refreshToken));
        token.setCreatedAt(Instant.now());
        token.setLastUsedAt(Instant.now());
        token.setExpiresAt(jwtUtil.getExpiration(refreshToken).toInstant());
        token.setRevoked(false);
        deviceTokenRepository.save(token);

        var tokenVO = new dev.ouanu.iems.vo.TokenVO(accessToken, refreshToken, jwtUtil.getExpiration(accessToken));
        return ResponseEntity.ok(tokenVO);
    }

    public ResponseEntity<dev.ouanu.iems.vo.TokenVO> refreshToken(String refreshToken) {
        var optional = deviceTokenRepository.findByRefreshTokenHashAndRevokedFalse(TokenUtils.sha256Hex(refreshToken));
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        var token = optional.get();
        if (token.isRevoked()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            deviceTokenRepository.save(token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            jwtUtil.verify(refreshToken);
        } catch (JWTVerificationException ex) {
            token.setRevoked(true);
            deviceTokenRepository.save(token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String accessJti = UUID.randomUUID().toString();
        String newAccessToken = jwtUtil.generateToken(token.getDeviceId(), false, accessJti);

        if (token.getExpiresAt().isBefore(Instant.now().plus(Duration.ofHours(48)))) {
            token.setRevoked(true);
            deviceTokenRepository.save(token);

            String newJti = UUID.randomUUID().toString();
            String newRefreshToken = jwtUtil.generateToken(token.getDeviceId(), true, newJti);
            DeviceToken newToken = new DeviceToken();
            newToken.setDeviceId(token.getDeviceId());
            newToken.setRefreshTokenHash(TokenUtils.sha256Hex(newRefreshToken));
            newToken.setCreatedAt(Instant.now());
            newToken.setLastUsedAt(Instant.now());
            newToken.setExpiresAt(jwtUtil.getExpiration(newRefreshToken).toInstant());
            newToken.setRevoked(false);
            deviceTokenRepository.save(newToken);

            var tokenVO = new dev.ouanu.iems.vo.TokenVO(newAccessToken, newRefreshToken, jwtUtil.getExpiration(newAccessToken));
            return ResponseEntity.ok(tokenVO);
        } else {
            token.setLastUsedAt(Instant.now());
            deviceTokenRepository.save(token);
            var tokenVO = new dev.ouanu.iems.vo.TokenVO(newAccessToken, refreshToken, jwtUtil.getExpiration(newAccessToken));
            return ResponseEntity.ok(tokenVO);
        }
    }

    @Transactional
    public ResponseEntity<String> logout(DeviceLogoutDTO dto) {
        var decoded = jwtUtil.verify(dto.getAccessToken());
        var jti = decoded.getId();
        var exp = decoded.getExpiresAt().toInstant();
        dev.ouanu.iems.entity.AccessTokenBlacklist blacklist = new dev.ouanu.iems.entity.AccessTokenBlacklist();
        blacklist.setJti(jti);
        blacklist.setExpiresAt(exp);
        blacklist.setReason("Device logout");
        blacklistRepository.save(blacklist);

        var optional = deviceTokenRepository.findByRefreshTokenHashAndRevokedFalse(TokenUtils.sha256Hex(dto.getRefreshToken()));
        if (optional.isPresent()) {
            var token = optional.get();
            token.setRevoked(true);
            deviceTokenRepository.save(token);
        }
        return ResponseEntity.ok("Logged out successfully");
    }

    
    
    public ResponseEntity<String> revokeRefreshToken(String refreshToken) {
        var optional = deviceTokenRepository.findByRefreshTokenHashAndRevokedFalse(TokenUtils.sha256Hex(refreshToken));
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Refresh token not found or already revoked");
        }
        var token = optional.get();
        token.setRevoked(true);
        deviceTokenRepository.save(token);
        return ResponseEntity.ok("Refresh token revoked successfully");
    }

    public ResponseEntity<String> revokeAccessToken(String accessToken) {
        var decoded = jwtUtil.verify(accessToken);
        var jti = decoded.getId();
        var exp = decoded.getExpiresAt().toInstant();
        dev.ouanu.iems.entity.AccessTokenBlacklist blacklist = new dev.ouanu.iems.entity.AccessTokenBlacklist();
        blacklist.setJti(jti);
        blacklist.setExpiresAt(exp);
        blacklist.setReason("Admin revoked access token");
        blacklistRepository.save(blacklist);
        return ResponseEntity.ok("Access token revoked successfully");
    }

    public DeviceVO getMyProfile() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof Long)) {
            return null;
        }
        Long deviceId = (Long) auth.getPrincipal();
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            return null;
        }
        return DeviceVO.fromEntity(device);
    }

    public ResponseEntity<String> verifyDeviceExists(String macAddress) {
        var device = deviceMapper.selectByMacAddress(macAddress);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("Device exists");
    }
}
