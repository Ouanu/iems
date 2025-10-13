package dev.ouanu.iems.dto;

import javax.validation.constraints.NotBlank;

import org.springframework.beans.BeanUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ouanu.iems.entity.Device;
import lombok.ToString;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterDeviceDTO {
    @NotBlank
    private String macAddress; // MAC 地址
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank
    private String signatureHash; // 设备签名的 SHA-256 哈希
    private Boolean active = true; // 设备是否激活
    private Boolean locked = false; // 设备是否锁定
    private String model; // 设备型号
    private String brand; // 设备品牌
    private String serialno; // 设备序列号, android 设备为 Build.SERIAL
    private String androidVersion; // 系统版本, android 版本号
    private String appVersion; // iems app version
    private String romVersion; // 设备rom版本, ro.build.fingerprint

    public static Device toEntity(RegisterDeviceDTO dto) {
        Device device = new Device();
        BeanUtils.copyProperties(dto, device);
        return device;
    }
}
