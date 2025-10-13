package dev.ouanu.iems.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.ouanu.iems.constant.Permission;
import dev.ouanu.iems.service.PermissionService;
import dev.ouanu.iems.vo.PermissionVO;

@RestController
@RequestMapping("/api")
public class PermissionController {
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    // Controller methods would go here
    @PreAuthorize("hasAuthority('operator:manage')")
    @PostMapping("/admin/permissions")
    public ResponseEntity<String> createPermission(@RequestParam Long operatorId, @RequestBody String permissionStr) {
        try {
            Permission permission = Permission.fromType(permissionStr.replace("\"", "")); // 去掉可能的引号

            if (permissionService.createPermission(operatorId, permission)) {
                return ResponseEntity.ok("Permission created successfully");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error creating permission");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating permission: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('operator:manage')")
    @PutMapping("/admin/permissions/{id}")
    public ResponseEntity<String> updatePermission(@PathVariable Long id, @RequestBody String permissionStr) {
        try {
            Permission[] permission;
            String[] permissions = permissionStr.split(","); // 检查是否包含逗号
            Set<Permission> permissionSet = Arrays.stream(permissions)
                    .map(p -> Permission.fromType(p.replace("\"", "")))
                    .collect(Collectors.toSet());
            permission = permissionSet.toArray(Permission[]::new);
            if (permissionService.updatePermission(id, permission)) {
                return ResponseEntity.ok("Permission updated successfully");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error updating permission");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating permission: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('operator:manage')")
    @DeleteMapping("/admin/permissions/{id}")
    public ResponseEntity<String> deletePermission(@PathVariable Long id) {
        try {
            if (permissionService.deletePermission(id)) {
                return ResponseEntity.ok("Permission deleted successfully");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error deleting permission");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting permission");
        }
    }

    @PreAuthorize("hasAuthority('operator:manage')")
    @GetMapping("/admin/permissions/query")
    public ResponseEntity<List<PermissionVO>> queryPermissions(@RequestParam Map<String, Object> params) {
        try {
            // 手动处理 offset 和 limit 参数
            int offset = Integer.parseInt(params.getOrDefault("offset", "0").toString());
            int limit = Integer.parseInt(params.getOrDefault("limit", "20").toString());
            params.put("offset", offset);
            params.put("limit", limit);

            List<PermissionVO> permissions = permissionService.queryPermissions(params);
            return ResponseEntity.ok(permissions);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasAuthority('operator:manage')")
    @GetMapping("/admin/permissions")
    public ResponseEntity<List<PermissionVO>> listPermissions(@RequestParam int offset, @RequestParam int limit) {
        try {
            List<PermissionVO> permissions = permissionService.listPermissions(offset, limit);
            return ResponseEntity.ok(permissions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/auth/verify")
    public ResponseEntity<Boolean> verifyPermission(@RequestParam Long operatorId,
            @RequestParam Permission permission) {
        try {
            boolean hasPermission = permissionService.verifyPermission(operatorId, permission);
            return ResponseEntity.ok(hasPermission);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasAuthority('operator:manage')")
    @GetMapping("/admin/permissions/{id}")
    public ResponseEntity<PermissionVO> getPermissionById(@PathVariable Long id) {
        try {
            PermissionVO permission = permissionService.getPermissionVOById(id);
            if (permission != null) {
                return ResponseEntity.ok(permission);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    

}
