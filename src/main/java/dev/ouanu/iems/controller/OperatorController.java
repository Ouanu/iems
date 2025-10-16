package dev.ouanu.iems.controller;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import dev.ouanu.iems.annotation.ActionLog;
import dev.ouanu.iems.dto.AdminResetPasswordDTO;
import dev.ouanu.iems.dto.BatchUpdateOperatorsRequest;
import dev.ouanu.iems.dto.ChangePasswordDTO;
import dev.ouanu.iems.dto.OperatorLogoutDTO;
import dev.ouanu.iems.dto.RegisterOperatorDTO;
import dev.ouanu.iems.dto.UpdateOperatorDTO;
import dev.ouanu.iems.entity.Operator;
import dev.ouanu.iems.service.OperatorService;
import dev.ouanu.iems.vo.OperatorVO;
import dev.ouanu.iems.vo.TokenVO;

/**
 * RESTful controller for operator/auth related APIs.
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperatorController {

    private final OperatorService operatorService;

    public OperatorController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    // ----------------- Operator management -----------------

    // Create operator (admin)
    @ActionLog("创建设备")
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/admin/operators", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OperatorVO> createOperator(@Valid @RequestBody RegisterOperatorDTO dto) {
        try {
            Operator operator = operatorService.createOperator(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(OperatorVO.fromEntity(operator));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Admin update profile
    @ActionLog("更新操作员")
    @PreAuthorize("hasAuthority('operator:write')")
    @PutMapping(path = "/admin/operators/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OperatorVO> adminUpdateProfile(@PathVariable("id") Long id,
                                                     @Valid @RequestBody UpdateOperatorDTO dto) {
        try {
            Operator operator = operatorService.adminUpdateProfile(id, dto);
            return ResponseEntity.ok(OperatorVO.fromEntity(operator));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Admin batch update operators
    @ActionLog("批量更新操作员")
    @PreAuthorize("hasAuthority('operator:write')")
    @PutMapping(path = "/admin/operators/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> batchUpdateOperators(@Valid @RequestBody BatchUpdateOperatorsRequest request) {
        try {
            operatorService.adminBatchUpdateOperators(request.getIds(), request.getUpdates());
            return ResponseEntity.ok("批量更新成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Admin reset password
    @ActionLog("重置操作员密码")
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/operators/{id}/password/reset", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> adminResetPassword(@PathVariable("id") Long id,
                                                     @Valid @RequestBody AdminResetPasswordDTO dto) {
        // ensure dto.id matches path id (or set it)
        dto.setId(id);
        try {
            operatorService.adminResetPassword(dto);
            return ResponseEntity.ok("Password reset successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Operator update own profile
    @ActionLog("更新个人信息")
    @PreAuthorize("hasAuthority('operator:write')")
    @PutMapping(path = "/admin/operators/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OperatorVO> updateProfile(@Valid @RequestBody UpdateOperatorDTO dto) {
        try {
            OperatorVO updatedOperator = operatorService.updateProfile(dto);
            return ResponseEntity.ok(updatedOperator);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // ----------------- Authentication / Token -----------------

    public static class LoginRequest {
        public final String phone;
        public final String password;

        public LoginRequest(String phone, String password) {
            this.phone = phone;
            this.password = password;
        }
    }

    @ActionLog("操作员登录")
    @PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenVO> login(@Valid @RequestBody LoginRequest req) {
        return operatorService.login(req.phone, req.password);
    }

    public static class RefreshRequest {
        public final String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }
    @ActionLog("操作员刷新令牌")
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/auth/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenVO> refresh(@Valid @RequestBody RefreshRequest req) {
        return operatorService.refreshToken(req.refreshToken);
    }

    @ActionLog("操作员登出")
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/auth/logout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> logout(@Valid @RequestBody OperatorLogoutDTO dto) {
        return operatorService.logout(dto);
    }

    // Revoke refresh token (admin or user)
    public static class SimpleTokenRequest {
        public final String token;

        public SimpleTokenRequest(String token) {
            this.token = token;
        }
    }

    @ActionLog("撤销操作员刷新令牌")
    @PreAuthorize("hasAuthority('operator:write')")
    @DeleteMapping(path = "/admin/tokens/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> revokeRefresh(@Valid @RequestBody SimpleTokenRequest req) {
        return operatorService.revokeRefreshToken(req.token);
    }

    @ActionLog("撤销操作员访问令牌")
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/admin/tokens/access/revoke", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> revokeAccess(@Valid @RequestBody SimpleTokenRequest req) {
        return operatorService.revokeAccessToken(req.token);
    }

    // ----------------- Password -----------------

    @ActionLog("修改密码")
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/admin/operators/password/change", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        return operatorService.changePassword(dto);
    }

    // List operators (admin) with pagination
    @PreAuthorize("hasAuthority('operator:read')")
    @GetMapping(path = "/admin/operators")
    public ResponseEntity<List<OperatorVO>> listOperators(
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        List<OperatorVO> operatorVOs = operatorService.listOperators(offset, limit);
        if (operatorVOs == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(operatorVOs);
    }

    // Query operators (admin) with a JSON body of params
    @PreAuthorize("hasAuthority('operator:read')")
    @PostMapping(path = "/admin/operators/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OperatorVO>> queryOperators(@RequestBody(required = false) Map<String, Object> params) {
        try {
            List<OperatorVO> operators = operatorService.query(params);
            return ResponseEntity.ok(operators);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Delete operator (admin)
    @ActionLog("删除操作员")
    @PreAuthorize("hasAuthority('operator:delete')")
    @DeleteMapping(path = "/admin/operators/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deleteOperator(@PathVariable("id") Long id) {
        try {
            operatorService.delete(id);
            return ResponseEntity.ok("Operator deleted successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Get operator by ID (admin)
    @PreAuthorize("hasAuthority('operator:read')")
    @GetMapping(path = "/admin/operators/{id}")
    public ResponseEntity<OperatorVO> getOperator(@PathVariable("id") Long id) {
        OperatorVO operator = operatorService.getOperator(id);
        if (operator == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(operator);
    }

    
}