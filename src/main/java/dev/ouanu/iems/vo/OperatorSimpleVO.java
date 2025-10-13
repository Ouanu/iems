package dev.ouanu.iems.vo;

import org.springframework.beans.BeanUtils;

import dev.ouanu.iems.entity.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OperatorSimpleVO {
    private String uuid;
    private String displayName;
    private String phone;
    private String email;
    private String accountType;
    private Boolean active;

    public static OperatorSimpleVO fromEntity(Operator operator) {
        if (operator == null) return null;
        OperatorSimpleVO vo = new OperatorSimpleVO();
        BeanUtils.copyProperties(operator, vo);
        return vo;
    }
}
