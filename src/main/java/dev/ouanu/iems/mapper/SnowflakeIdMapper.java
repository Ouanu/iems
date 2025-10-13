package dev.ouanu.iems.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import dev.ouanu.iems.entity.SnowflakeId;

@Mapper
public interface SnowflakeIdMapper {
    void createTableIfNotExists();
    int insert(SnowflakeId snowflakeId);
    SnowflakeId selectById(@Param("id") Long id);
}
