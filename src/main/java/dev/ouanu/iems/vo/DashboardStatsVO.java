package dev.ouanu.iems.vo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardStatsVO implements Serializable {
    private long operatorCount;
    private long deviceCount;
    private long apkCount;
}
