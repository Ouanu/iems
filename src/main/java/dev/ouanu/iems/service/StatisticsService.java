package dev.ouanu.iems.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ouanu.iems.mapper.DeviceMapper;
import dev.ouanu.iems.mapper.OperatorMapper;
import dev.ouanu.iems.repository.ApkRepository;
import dev.ouanu.iems.vo.DashboardStatsVO;

@Service
public class StatisticsService {

    private final OperatorMapper operatorMapper;
    private final DeviceMapper deviceMapper;
    private final ApkRepository apkRepository;

    public StatisticsService(OperatorMapper operatorMapper,
                             DeviceMapper deviceMapper,
                             ApkRepository apkRepository) {
        this.operatorMapper = operatorMapper;
        this.deviceMapper = deviceMapper;
        this.apkRepository = apkRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsVO getDashboardStats() {
        long operatorCount = safeCountOperators();
        long deviceCount = safeCountDevices();
        long apkCount = safeCountApks();
        return new DashboardStatsVO(operatorCount, deviceCount, apkCount);
    }

    private long safeCountOperators() {
        try {
            return operatorMapper.count();
        } catch (Exception ex) {
            return 0L;
        }
    }

    private long safeCountDevices() {
        try {
            return deviceMapper.count();
        } catch (Exception ex) {
            return 0L;
        }
    }

    private long safeCountApks() {
        try {
            return apkRepository.count();
        } catch (Exception ex) {
            return 0L;
        }
    }
}
