package dev.ouanu.iems.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.ouanu.iems.service.PermissionService;
import dev.ouanu.iems.service.TokenService;
import dev.ouanu.iems.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final TokenService tokenService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, PermissionService permissionService, TokenService tokenService) {
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
        this.tokenService = tokenService;
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("access_token")) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        
        try {
            if (!jwtUtil.validate(token)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
            if (tokenService.isTokenBlacklisted(jwtUtil.getJti(token))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token is blacklisted");
                return;
            }
            Long id = jwtUtil.getSubjectAsLong(token);
            if (id != null) {
                var permissionVO = permissionService.getPermissionVOById(id);
                if (permissionVO == null) {
                    throw new IllegalStateException("Operator has no permissions");
                }
                List<SimpleGrantedAuthority> authorities = List.of();
                if (permissionVO.getPermissions() != null && !permissionVO.getPermissions().isBlank()) {
                    authorities = Arrays.stream(permissionVO.getPermissions().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(SimpleGrantedAuthority::new) // 权限形如 "operator:read"
                            .toList();
                }
                var auth = new UsernamePasswordAuthenticationToken(id, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            filterChain.doFilter(request, response);
        } catch (IOException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
        } 
    }
}
