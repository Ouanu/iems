package dev.ouanu.iems.controller;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.ouanu.iems.annotation.ActionLog;
import dev.ouanu.iems.dto.BatchUpdateDevicesRequest;
import dev.ouanu.iems.dto.DeviceLogoutDTO;
import dev.ouanu.iems.dto.RegisterDeviceDTO;
import dev.ouanu.iems.dto.UpdateDeviceDTO;
import dev.ouanu.iems.entity.Device;
import dev.ouanu.iems.service.DeviceService;
import dev.ouanu.iems.vo.DeviceVO;
import dev.ouanu.iems.vo.TokenVO;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    // Create device (admin)
    @ActionLog("创建设备")
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/admin/devices", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createDevice(@Valid @RequestBody RegisterDeviceDTO dto) {
        try {
            Device device = deviceService.registerDevice(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(DeviceVO.fromEntity(device));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @AllArgsConstructor
    public static class RegisterRequest {
        public final String macAddress;
        public final String signatureHash;
        public final String model;
        public final String brand;
        public final String serialno;
        public final String androidVersion;
        public final String appVersion;
        public final String romVersion;
    }

    // Admin update device by id
    @ActionLog("更新设备")
    @PreAuthorize("hasAuthority('operator:write')")
    @PutMapping(path = "/admin/devices/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> adminUpdateDevice(@PathVariable("id") Long id,
                                                    @Valid @RequestBody UpdateDeviceDTO dto) {
        try {
            Device device = deviceService.updateDevice(id, dto);
            return ResponseEntity.ok(DeviceVO.fromEntity(device));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('operator:write')")
    @ActionLog("批量更新设备")
    @PutMapping(path = "/admin/devices/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> batchUpdateDevices(@Valid @RequestBody BatchUpdateDevicesRequest request) {
        try {
            deviceService.adminBatchUpdateDevices(request.getIds(), request.getActive(), request.getLocked());
            return ResponseEntity.ok("批量更新成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // List devices (admin)
    @PreAuthorize("hasAuthority('operator:read')")
    @GetMapping(path = "/admin/devices")
    public ResponseEntity<List<DeviceVO>> listDevices(
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        try {
            List<DeviceVO> devices = deviceService.listDevices(offset, limit);
            return ResponseEntity.ok(devices);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Query devices (admin)
    @PreAuthorize("hasAuthority('operator:read')")
    @PostMapping(path = "/admin/devices/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DeviceVO>> queryDevices(@RequestBody(required = false) Map<String, Object> params) {
        System.out.println("Querying devices with params: " + params);
        try {
            List<DeviceVO> devices = deviceService.queryDevices(params);
            return ResponseEntity.ok(devices);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Delete device (admin)
    @ActionLog("删除设备")
    @PreAuthorize("hasAuthority('operator:delete')")
    @DeleteMapping(path = "/admin/devices/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deleteDevice(@PathVariable("id") Long id) {
        System.out.println("Deleting device with id: " + id);
        try {
            deviceService.deleteDevice(id);
            return ResponseEntity.ok("Device deleted successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Get device by id (admin)
    @PreAuthorize("hasAuthority('operator:read')")
    @GetMapping(path = "/admin/devices/{id}")
    public ResponseEntity<DeviceVO> getDevice(@PathVariable("id") Long id) {
        DeviceVO device = deviceService.getDeviceById(id);
        return device != null ? ResponseEntity.ok(device) : ResponseEntity.notFound().build();
    }

    // Get device by uuid (public/admin)
    @GetMapping(path = "/devices/uuid/{uuid}")
    public ResponseEntity<DeviceVO> getDeviceByUuid(@PathVariable("uuid") String uuid) {
        DeviceVO device = deviceService.getDeviceByUuid(uuid);
        return device != null ? ResponseEntity.ok(device) : ResponseEntity.notFound().build();
    }

    @GetMapping(path = "/devices/auth/verify/{macAddress}")
    public ResponseEntity<String> verifyDeviceExists(@PathVariable String macAddress) {
        return deviceService.verifyDeviceExists(macAddress);
    }
    

    // ----------------- Device authentication -----------------
    public static class LoginRequest {
        public final String macAddress;
        public final String signatureHash;

        public LoginRequest(String macAddress, String signatureHash) {
            this.macAddress = macAddress;
            this.signatureHash = signatureHash;
        }
    }

    @ActionLog("设备注册")
    @PostMapping(path="/devices/auth", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        RegisterDeviceDTO dto = new RegisterDeviceDTO(request.macAddress, request.signatureHash, true, false, request.model,
                request.brand, request.serialno, request.androidVersion, request.appVersion, request.romVersion);
        try {
            Device device = deviceService.registerDevice(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(DeviceVO.fromEntity(device));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @ActionLog("设备登录")
    @PostMapping(path = "/devices/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenVO> login(@Valid @RequestBody LoginRequest req) {
        return deviceService.login(req.macAddress, req.signatureHash);
    }

    public static class RefreshRequest {
        public final String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    @ActionLog("刷新设备令牌")
    @PreAuthorize("hasAuthority('device:write:self')")
    @PostMapping(path = "/devices/auth/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenVO> refresh(@Valid @RequestBody RefreshRequest req) {
        System.out.println("Refreshing token for refresh token: " + req.refreshToken);
        return deviceService.refreshToken(req.refreshToken);
    }

    @ActionLog("设备登出")
    @PreAuthorize("hasAuthority('device:write:self')")
    @PostMapping(path = "/devices/auth/logout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> logout(@Valid @RequestBody DeviceLogoutDTO dto) {
        return deviceService.logout(dto);
    }

    public static class UpdateDeviceRequest {
        public final String model;
        public final String brand;
        public final String serialno;
        public final String androidVersion;
        public final String appVersion;
        public final String romVersion;

        public UpdateDeviceRequest(String model, String brand, String serialno, String androidVersion, String appVersion,
                                 String romVersion) {
            this.model = model;
            this.brand = brand;
            this.serialno = serialno;
            this.androidVersion = androidVersion;
            this.appVersion = appVersion;
            this.romVersion = romVersion;
        }
    }

    @ActionLog("更新设备信息")
    @PreAuthorize("hasAuthority('device:write:self')")
    @PutMapping(path="/devices/auth/me", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> updateDeviceAuth(@Valid @RequestBody UpdateDeviceRequest dto) {
        var updateDto = new UpdateDeviceDTO(dto.model, dto.brand, dto.serialno, dto.androidVersion,
                dto.appVersion, dto.romVersion, true, false);
        try {
            String result = deviceService.updateMyProfile(updateDto);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    public static class DeviceResponse {
        public final String uuid;
        public final Boolean active;
        public final Boolean locked;
        public final String model;
        public final String brand;
        public final String serialno;
        public final String androidVersion;
        public final String appVersion;
        public final String romVersion;

        public DeviceResponse(String uuid, String macAddress, String signatureHash, Boolean active,
                              Boolean locked, String model, String brand, String serialno, String androidVersion,
                              String appVersion, String romVersion) {
            this.uuid = uuid;
            this.active = active;
            this.locked = locked;
            this.model = model;
            this.brand = brand;
            this.serialno = serialno;
            this.androidVersion = androidVersion;
            this.appVersion = appVersion;
            this.romVersion = romVersion;
        }

        public static DeviceResponse fromEntity(DeviceVO device) {
            if (device == null) return null;
            var resp = new DeviceResponse(device.getUuid(), null, null, device.getActive(), device.getLocked(),
                    device.getModel(), device.getBrand(), device.getSerialno(), device.getAndroidVersion(),
                    device.getAppVersion(), device.getRomVersion());
            return resp;
        }
    }

    @PreAuthorize("hasAuthority('device:read:self')")
    @GetMapping(path = "/devices/auth/me")
    public ResponseEntity<DeviceResponse> getMyProfile() {
        var device = deviceService.getMyProfile();
        if (device == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(DeviceResponse.fromEntity(device));
    }

    // Admin revoke refresh
    @ActionLog("撤销设备刷新令牌")
    @PreAuthorize("hasAuthority('operator:write')")
    @DeleteMapping(path = "/admin/devices/tokens/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> revokeRefresh(@Valid @RequestBody Map<String, String> body) {
        String token = body.get("token");
        return deviceService.revokeRefreshToken(token);
    }

    // Admin revoke access
    @ActionLog("撤销设备访问令牌")
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/admin/devices/tokens/access/revoke", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> revokeAccess(@Valid @RequestBody Map<String, String> body) {
        String token = body.get("token");
        return deviceService.revokeAccessToken(token);
    }

    
}