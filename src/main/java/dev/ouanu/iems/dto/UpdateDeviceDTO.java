package dev.ouanu.iems.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateDeviceDTO {
    private String model;
    private String brand;
    private String serialno;
    private String androidVersion;
    private String appVersion;
    private String romVersion;
    private Boolean active;
    private Boolean locked;
}
