package dev.ouanu.iems.vo;

import org.springframework.beans.BeanUtils;

import dev.ouanu.iems.entity.Device;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceSimpleVO {
    private String uuid;
    private String macAddress;
    private Long customerId;
    private Boolean active;
    private Boolean locked;
    private String customerGroup;
    private String model;
    private String brand;
    private String androidVersion;
    private String appVersion;
    private String romVersion;

    public static DeviceSimpleVO fromEntity(Device device) {
        if (device == null) return null;
        DeviceSimpleVO vo = new DeviceSimpleVO();
        BeanUtils.copyProperties(device, vo);
        return vo;
    }
}
