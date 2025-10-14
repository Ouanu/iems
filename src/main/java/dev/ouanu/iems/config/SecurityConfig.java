package dev.ouanu.iems.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import dev.ouanu.iems.filter.JwtAuthenticationFilter;
import dev.ouanu.iems.service.PermissionService;
import dev.ouanu.iems.service.TokenService;
import dev.ouanu.iems.util.JwtUtil;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtUtil jwtUtil, PermissionService permissionService, TokenService tokenService) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(jwtUtil, permissionService, tokenService);
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login.html", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/devices/auth/**").permitAll()
                .requestMatchers("/api/admin/**").authenticated()
                .anyRequest().permitAll()
                // .requestMatchers("/api/**").permitAll()
                // .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
