package dev.ouanu.iems.vo;

import java.io.Serializable;
import java.time.Instant;

import org.springframework.beans.BeanUtils;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import dev.ouanu.iems.entity.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OperatorVO implements Serializable{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String uuid;
    private String displayName;
    private String phone;
    private String email;
    private String accountType;
    private String department;
    private String team;
    private String position;
    private String level;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static OperatorVO fromEntity(Operator operator) {
        if (operator == null) return null;
        OperatorVO vo = new OperatorVO();
        BeanUtils.copyProperties(operator, vo);
        return vo;
    }
}
