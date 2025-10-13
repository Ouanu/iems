package dev.ouanu.iems.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import dev.ouanu.iems.entity.Operator;

@Mapper
public interface OperatorMapper {
    void createTableIfNotExists();
    int insert(Operator operator);
    Operator selectById(@Param("id") Long id);
    Operator selectByUuid(@Param("uuid") String uuid);
    Operator selectByEmail(@Param("email") String email);
    Operator selectByPhone(@Param("phone") String phone);
    int update(Operator operator);
    int deleteById(@Param("id") Long id);
    List<Operator> list(@Param("offset") int offset, @Param("limit") int limit);
    List<Operator> query(Map<String, Object> params);
    long count();
    boolean existsByPhone(@Param("phone") String phone);
    boolean existsByEmail(@Param("email") String email);
}
