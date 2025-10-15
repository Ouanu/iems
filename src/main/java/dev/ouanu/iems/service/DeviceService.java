package dev.ouanu.iems.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.auth0.jwt.exceptions.JWTVerificationException;

import dev.ouanu.iems.constant.BizType;
import dev.ouanu.iems.constant.Permission;
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
    private final PermissionService permissionService;
    private final CacheManager cacheManager;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    public DeviceService(DeviceMapper deviceMapper,
                         JwtUtil jwtUtil,
                         DeviceTokenRepository deviceTokenRepository,
                         AccessTokenBlacklistRepository blacklistRepository, SnowflakeIdService snowflakeIdService, PermissionService permissionService, CacheManager cacheManager) {
        this.deviceMapper = deviceMapper;
        this.jwtUtil = jwtUtil;
        this.deviceTokenRepository = deviceTokenRepository;
        this.blacklistRepository = blacklistRepository;
        this.snowflakeIdService = snowflakeIdService;
        this.permissionService = permissionService;
        this.cacheManager = cacheManager;
    }

    @Transactional
    @CacheEvict(value = {"devices:list","devices:byId","devices:byUuid"}, allEntries = true)
    public Device registerDevice(RegisterDeviceDTO dto) {
        var dm = deviceMapper.selectByMacAddress(dto.getMacAddress());
        if (dm != null) {
            throw new IllegalStateException("Device with this MAC address already exists");
        }
        Device device = RegisterDeviceDTO.toEntity(dto);
        device.setId(snowflakeIdService.nextIdAndPersist(BizType.DEVICE));
        device.setUuid(UUID.randomUUID().toString());
        // default values if null
        if (device.getActive() == null) device.setActive(true);
        if (device.getLocked() == null) device.setLocked(false);
        int ret = deviceMapper.insert(device);
        if (ret != 1) {
            throw new IllegalStateException("Failed to create device");
        }
        var retP = permissionService.createPermission(device.getId(), Permission.DEVICE_READ_ITSELF, Permission.DEVICE_UPDATE_ITSELF, Permission.DEVICE_WRITE_ITSELF, Permission.APP_READ);
        if (retP) {
            throw new IllegalStateException("Failed to create device permissions");
        }
        return device;
    }

    @Transactional
    @CacheEvict(value = {"devices:list", "devices:byId", "devices:byUuid"}, key = "#id")
    public Device updateDevice(Long id, UpdateDeviceDTO dto) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("Device not found");
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
            throw new IllegalStateException("Failed to update device");
        }
        return device;
    }

    @Transactional
    public void adminBatchUpdateDevices(List<Long> ids, Boolean active, Boolean locked) {
        if (active == null && locked == null) {
            throw new IllegalArgumentException("请至少选择一个更新字段");
        }

        Set<Long> uniqueIds = normalizeIds(ids);
        List<Device> devices = loadDevices(uniqueIds);

        for (Device device : devices) {
            if (active != null) {
                device.setActive(active);
            }
            if (locked != null) {
                device.setLocked(locked);
            }
            int ret = deviceMapper.update(device);
            if (ret != 1) {
                throw new IllegalStateException("批量更新失败, 设备ID: " + device.getId());
            }
        }

        evictBatchCaches(uniqueIds, devices);
    }

    private Set<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("批量更新需要至少一个设备ID");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw new IllegalArgumentException("ID 不能为空");
            }
            uniqueIds.add(id);
        }
        return uniqueIds;
    }

    private List<Device> loadDevices(Set<Long> ids) {
        List<Device> devices = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Device device = deviceMapper.selectById(id);
            if (device == null) {
                throw new IllegalArgumentException("设备不存在: " + id);
            }
            devices.add(device);
        }
        return devices;
    }

    @Transactional
    @CacheEvict(value = {"devices:byId", "devices:byUuid"}, key = "#result.id")
    public String updateMyProfile(UpdateDeviceDTO dto) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof Long)) {
            throw new SecurityException("Unauthorized");
        }
        Long deviceId = (Long) auth.getPrincipal();
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("Device not found");
        }
        if (Boolean.FALSE.equals(device.getActive()) || Boolean.TRUE.equals(device.getLocked())) {
            throw new SecurityException("Device is inactive or locked");
        }
        device.setModel(dto.getModel());
        device.setBrand(dto.getBrand());
        device.setSerialno(dto.getSerialno());
        device.setAndroidVersion(dto.getAndroidVersion());
        device.setAppVersion(dto.getAppVersion());
        device.setRomVersion(dto.getRomVersion());
        int ret = deviceMapper.update(device);
        if (ret != 1) {
            throw new IllegalStateException("Failed to update device");
        }
        return "Device updated successfully";
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "devices:byId", key = "#id")
    public DeviceVO getDeviceById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            return null;
        }
        return DeviceVO.fromEntity(device);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "devices:byUuid", key = "#uuid")
    public DeviceVO getDeviceByUuid(String uuid) {
        Device device = deviceMapper.selectByUuid(uuid);
        if (device == null) {
            return null;
        }
        return DeviceVO.fromEntity(device);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "devices:list", key = "#offset + ':' + #limit")
    public List<DeviceVO> listDevices(int offset, int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        List<Device> devices = deviceMapper.list(offset, limit);
        
        return devices.stream().map(DeviceVO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceVO> queryDevices(Map<String, Object> params) {
        String offsetKey = "offset";
        String limitKey = "limit";
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("Query params cannot be empty");
        }
        // validate offset/limit if provided
        if (params.containsKey(offsetKey)) {
            int offset = (int) params.get(offsetKey);
            if (offset < 0) throw new IllegalArgumentException("Offset must be non-negative");
        }
        if (params.containsKey(limitKey)) {
            int limit = (int) params.get(limitKey);
            if (limit <= 0) params.put(limitKey, DEFAULT_LIMIT);
            else if (limit > MAX_LIMIT) params.put(limitKey, MAX_LIMIT);
        }
        List<Device> devices = deviceMapper.query(params);
        return devices.stream().map(DeviceVO::fromEntity).toList();
    }

    private void evictBatchCaches(Set<Long> ids, List<Device> devices) {
        if (cacheManager == null) {
            return;
        }
        Cache listCache = cacheManager.getCache("devices:list");
        if (listCache != null) {
            listCache.clear();
        }
        Cache byIdCache = cacheManager.getCache("devices:byId");
        if (byIdCache != null) {
            ids.forEach(byIdCache::evict);
        }
        Cache byUuidCache = cacheManager.getCache("devices:byUuid");
        if (byUuidCache != null) {
            for (Device device : devices) {
                if (Objects.nonNull(device.getUuid())) {
                    byUuidCache.evict(device.getUuid());
                }
            }
        }
    }

    @Transactional
    @CacheEvict(value = {"devices:list","devices:byId","devices:byUuid"}, allEntries = true)
    public void deleteDevice(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("Device not found");
        }
        int ret = deviceMapper.deleteById(id);
        if (ret != 1) {
            throw new IllegalStateException("Failed to delete device");
        }
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
