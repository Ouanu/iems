package dev.ouanu.iems.vo;

import java.time.Instant;

import org.springframework.beans.BeanUtils;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import dev.ouanu.iems.entity.Device;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String uuid;
    private String macAddress;
    private String signatureHash;
    private Boolean active;
    private Boolean locked;
    private Long customerId;
    private String customerGroup;
    private String model;
    private String brand;
    private String serialno;
    private String androidVersion;
    private String appVersion;
    private String romVersion;
    private Instant createdAt;
    private Instant updatedAt;

    public static DeviceVO fromEntity(Device device) {
        if (device == null) return null;
        DeviceVO vo = new DeviceVO();
        BeanUtils.copyProperties(device, vo);
        return vo;
    }
}
