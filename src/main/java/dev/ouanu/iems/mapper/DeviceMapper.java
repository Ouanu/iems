package dev.ouanu.iems.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import dev.ouanu.iems.entity.Device;

@Mapper
public interface DeviceMapper {
    void createTableIfNotExists();
    Device selectById(Long id);
    Device selectByUuid(String uuid);
    Device selectByMacAddress(String macAddress);
    int insert(Device device);
    int update(Device device);
    int deleteById(Long id);
    List<Device> list(@Param("offset") int offset, @Param("limit") int limit);
    List<Device> query(Map<String, Object> params);
    long count();
    boolean existsByUuid(String uuid);
}
