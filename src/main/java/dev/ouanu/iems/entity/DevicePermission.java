package dev.ouanu.iems.entity;

import java.time.Instant;
import java.util.Arrays;

import dev.ouanu.iems.constant.Permission;

public class DevicePermission {
    private Long deviceId;
    private String permissions; // 逗号分隔的权限字符串 
    private Instant createdAt;
    private Instant updatedAt;
// 工具方法：设置权限（从枚举转字符串）
    public void setPermissions(Permission... permission) {
        if (permission == null) {
            this.permissions = null;
            return;
        }
        // 将枚举的 types 转为逗号分隔字符串
        this.permissions = String.join(",", Arrays.stream(permission).flatMap(p -> Arrays.stream(p.getTypes())).toArray(String[]::new));
    }

    // 工具方法：获取权限（从字符串转枚举，匹配最佳权限）
    public Permission getPermissionsAsEnum() {
        if (permissions == null || permissions.isEmpty()) return null;

        // 尝试匹配所有权限类型
        for (Permission perm : Permission.values()) {
            String[] permTypes = perm.getTypes();
            String permStr = String.join(",", permTypes);
            if (permissions.equals(permStr)) {
                return perm;
            }
        }
        return null; // 无法匹配到具体权限
    }
    // Getters and Setters
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }

    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }

    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }
}
