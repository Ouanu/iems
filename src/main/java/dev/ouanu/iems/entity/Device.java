package dev.ouanu.iems.entity;

import java.io.Serializable;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Device implements Serializable{
    private Long id;
    private String uuid;
    private String macAddress; // MAC 地址
    private String signatureHash; // 设备签名的 SHA-256 哈希
    private Boolean active; // 设备是否激活
    private Boolean locked; // 设备是否锁定
    private Long customerId; // 设备所属客户,预留
    private String customerGroup; // 设备所属客户组,预留
    private String model; // 设备型号
    private String brand; // 设备品牌
    private String serialno; // 设备序列号, android 设备为 Build.SERIAL
    private String androidVersion; // 系统版本, android 版本号
    private String appVersion; // iems app version
    private String romVersion; // 设备rom版本, ro.build.fingerprint
    private Instant createdAt;
    private Instant updatedAt;

}
