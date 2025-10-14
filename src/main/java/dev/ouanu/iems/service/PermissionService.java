package dev.ouanu.iems.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ouanu.iems.constant.Permission;
import dev.ouanu.iems.entity.IdPermission;
import dev.ouanu.iems.mapper.PermissionMapper;
import dev.ouanu.iems.vo.PermissionVO;

@Service
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final Logger log = LoggerFactory.getLogger(PermissionService.class);

    public PermissionService(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    /**
     * Check if an operator has a specific permission.
     * 
     * @param Id
     * @param permission
     * @return
     */
    public boolean verifyPermission(Long Id, Permission permission) {
        var operatorPermission = permissionMapper.selectById(Id);
        if (operatorPermission == null || operatorPermission.getPermissions() == null) {
            return false;
        }
        return operatorPermission.hasPermission(permission.name());
    }

    /**
     * Create permissions for an operator.
     * 
     * @param Id
     * @param permissions
     * @return
     */
    @Transactional
    public boolean createPermission(Long Id, Permission... permissions) {
        try {
            IdPermission entity = new IdPermission();
            entity.setId(Id);
            entity.setPermissions(permissions);
            return permissionMapper.insert(entity) > 0;
        } catch (Exception e) {
            log.error("createPermission error for Id={}", Id, e);
            return false;
        }
    }

    /**
     * Update permissions for an operator.
     * 
     * @param Id
     * @param permissions
     * @return
     */
    @Transactional
    public boolean updatePermission(Long Id, Permission... permissions) {
        try {
            IdPermission entity = new IdPermission();
            entity.setId(Id);
            entity.setPermissions(permissions);
            if (permissionMapper.existsById(Id)) {
                return permissionMapper.update(entity) > 0;
            } else {
                return permissionMapper.insert(entity) > 0;
            }
        } catch (Exception e) {
            log.error("updatePermission error for Id={}", Id, e);
            return false;
        }
    }

    /**
     * Delete permissions for an operator.
     * 
     * @param Id
     * @return
     */
    @Transactional
    public boolean deletePermission(Long Id) {
        try {
            return permissionMapper.deleteById(Id) > 0;
        } catch (Exception e) {
            log.error("deletePermission error for Id={}", Id, e);
            return false;
        }
    }

    /**
     * Get PermissionVO by operator ID.
     * 
     * @param Id
     * @return
     */
    public PermissionVO getPermissionVOById(Long Id) {
        var operatorPermission = permissionMapper.selectById(Id);
        return PermissionVO.fromEntity(operatorPermission);
    }

    /**
     * List permissions with pagination.
     * 
     * @param offset
     * @param limit
     * @return
     */
    @Transactional(readOnly = true)
    public List<PermissionVO> listPermissions(int offset, int limit) {
        try {
            List<IdPermission> entities = permissionMapper.list(offset, limit);
            return entities.stream().map(this::toVO).toList();
        } catch (Exception e) {
            // 临时处理：如果数据库查询失败，返回空列表
            log.error("listPermissions error: offset={}, limit={}", offset, limit, e);
            return List.of();
        }
    }

    public long countPermissions() {
        return permissionMapper.count();
    }

    /**
     * Query permissions based on parameters.
     * 
     * @param params
     * @return
     */
    @Transactional(readOnly = true)
    public List<PermissionVO> queryPermissions(Map<String, Object> params) {
        try {
            String offsetKey = "offset";
            String limitKey = "limit";
            if (params == null || params.isEmpty()) {
                return List.of();
            }
            if (!params.containsKey(offsetKey)) {
                return List.of();
            } else {
                int offset = (int) params.get(offsetKey);
                if (offset < 0) {
                    return List.of();
                }
            }
            if (!params.containsKey(limitKey)) {
                return List.of();
            } else {
                int limit = (int) params.get(limitKey);
                if (limit <= 0) {
                    params.put(limitKey, DEFAULT_LIMIT);
                } else if (limit > MAX_LIMIT) {
                    params.put(limitKey, MAX_LIMIT);
                }
            }
            List<IdPermission> entities = permissionMapper.query(params);
            return entities.stream().map(this::toVO).toList();
        } catch (Exception e) {
            log.error("queryPermissions error: params={}", params, e);
            return List.of();
        }
    }

    private PermissionVO toVO(IdPermission entity) {
        PermissionVO vo = new PermissionVO();
        vo.setId(entity.getId());
        vo.setPermissions(entity.getPermissions()); // 直接使用字符串
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
