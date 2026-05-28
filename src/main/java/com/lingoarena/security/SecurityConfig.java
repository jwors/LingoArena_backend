package com.lingoarena.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置。
 *
 * 这是整个应用的"大门"，配置了：
 * 1. 哪些 URL 需要登录才能访问
 * 2. 使用 JWT 做认证（无状态，不用 Session）
 * 3. CORS 跨域配置（让前端能调用 API）
 * 4. 密码加密方式（BCrypt）
 *
 * 关键理解：
 * - /api/auth/register 和 /api/auth/login 是公开的（permitAll）
 * - 其他 /api/** 都需要有效的 JWT token
 * - CSRF 禁用：因为我们是前后端分离 + JWT，不需要 CSRF 保护
 * - 无状态 Session：不创建 HttpSession，每次请求独立认证
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * 核心安全过滤链。
     * 配置顺序：CORS → CSRF → Session → URL 权限 → JWT 过滤器
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS 配置（使用下面定义的 corsConfigurationSource Bean）
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 禁用 CSRF（前后端分离不需要）
            .csrf(csrf -> csrf.disable())
            // 无状态 Session（不创建也不使用 HttpSession）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // URL 权限配置
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            // 在 UsernamePasswordAuthenticationFilter 之前插入 JWT 过滤器
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 配置。
     * 注意：CORS 必须配在 SecurityConfig 里，而不是单独写一个 WebMvcConfigurer。
     * 因为在 Spring Security 项目中，Security 过滤链优先级高于 MVC 配置。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** BCrypt 密码加密器。对同样的密码每次加密结果都不同（自动加盐） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
