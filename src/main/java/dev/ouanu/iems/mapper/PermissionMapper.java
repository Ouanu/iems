package dev.ouanu.iems.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import dev.ouanu.iems.entity.IdPermission;

@Mapper
public interface PermissionMapper {
    void createTableIfNotExists();
    IdPermission selectById(@Param("id") Long id);
    boolean existsById(@Param("id") Long id);
    int insert(IdPermission permission);
    int update(IdPermission permission);
    int deleteById(@Param("id") Long id);
    List<IdPermission> list(@Param("offset") int offset, @Param("limit") int limit);
    List<IdPermission> query(Map<String, Object> params);
    long count();
}
