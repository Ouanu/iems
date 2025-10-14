package dev.ouanu.iems.vo;

import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import dev.ouanu.iems.entity.IdPermission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionVO implements Serializable{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String permissions;
    private Instant createdAt;
    private Instant updatedAt;

    public static PermissionVO fromEntity(IdPermission entity) {
        if (entity == null) return null;
        PermissionVO vo = new PermissionVO();
        vo.setId(entity.getId());
        vo.setPermissions(entity.getPermissions());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
