package dev.ouanu.iems.controller;

import java.util.List;

import javax.validation.Valid;

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

import dev.ouanu.iems.dto.AdminResetPasswordDTO;
import dev.ouanu.iems.dto.ChangePasswordDTO;
import dev.ouanu.iems.dto.OperatorLogoutDTO;
import dev.ouanu.iems.dto.RegisterOperatorDTO;
import dev.ouanu.iems.dto.UpdateOperatorDTO;
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
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/admin/operators", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> createOperator(@Valid @RequestBody RegisterOperatorDTO dto) {
        return operatorService.createOperator(dto);
    }

    // Admin update profile
    @PreAuthorize("hasAuthority('operator:write')")
    @PutMapping(path = "/admin/operators/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> adminUpdateProfile(@PathVariable("id") Long id,
                                                     @Valid @RequestBody UpdateOperatorDTO dto) {
        return operatorService.adminUpdateProfile(id, dto);
    }

    // Admin reset password
    @PreAuthorize("hasAuthority('operator:write')")
    @PostMapping(path = "/operators/{id}/password/reset", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> adminResetPassword(@PathVariable("id") Long id,
                                                     @Valid @RequestBody AdminResetPasswordDTO dto) {
        // ensure dto.id matches path id (or set it)
        dto.setId(id);
        return operatorService.adminResetPassword(dto);
    }

    // Operator update own profile
    @PreAuthorize("hasAuthority('operator:write')")
    @PutMapping(path = "/admin/operators/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OperatorVO> updateProfile(@Valid @RequestBody UpdateOperatorDTO dto) {
        return operatorService.updateProfile(dto);
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

    @PostMapping(path = "/auth/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenVO> refresh(@Valid @RequestBody RefreshRequest req) {
        return operatorService.refreshToken(req.refreshToken);
    }

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

    
    @DeleteMapping(path = "/admin/tokens/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> revokeRefresh(@Valid @RequestBody SimpleTokenRequest req) {
        return operatorService.revokeRefreshToken(req.token);
    }

    @PostMapping(path = "/admin/tokens/access/revoke", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> revokeAccess(@Valid @RequestBody SimpleTokenRequest req) {
        return operatorService.revokeAccessToken(req.token);
    }

    // ----------------- Password -----------------

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
        return operatorService.listOperators(offset, limit);
    }

    // Query operators (admin) with a JSON body of params
    @PreAuthorize("hasAuthority('operator:read')")
    @PostMapping(path = "/admin/operators/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OperatorVO>> queryOperators(@RequestBody(required = false) java.util.Map<String, Object> params) {
        return operatorService.query(params);
    }

    // Delete operator (admin)
    @PreAuthorize("hasAuthority('operator:delete')")
    @DeleteMapping(path = "/admin/operators/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deleteOperator(@PathVariable("id") Long id) {
        return operatorService.delete(id);
    }

    // Get operator by ID (admin)
    @PreAuthorize("hasAuthority('operator:read')")
    @GetMapping(path = "/admin/operators/{id}")
    public ResponseEntity<OperatorVO> getOperator(@PathVariable("id") Long id) {
        return operatorService.getOperator(id);
    }

    
}