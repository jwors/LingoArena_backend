package com.lingoarena.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器。
 *
 * OncePerRequestFilter：确保每个请求只经过一次这个过滤器。
 *
 * 工作流程：
 * 1. 从请求头 Authorization 中提取 Bearer token
 * 2. 验证 token 是否有效（签名 + 过期时间）
 * 3. 如果有效，将用户 ID 设置到 SecurityContext 中
 * 4. Controller 可以通过 @AuthenticationPrincipal Long userId 获取
 *
 * 注意：只拦截 REST API 请求（/api/**），不处理 WebSocket 握手。
 * WebSocket 的认证在 JwtHandshakeInterceptor 中处理。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtTokenService.validateToken(token)) {
            Long userId = jwtTokenService.getUserIdFromToken(token);
            // 将 userId 存入 SecurityContext，后续 Controller 能通过 @AuthenticationPrincipal 获取
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 无论是否有 token，都放行——SecurityConfig 中配置了哪些路径需要认证
        filterChain.doFilter(request, response);
    }

    /** 从请求头中提取 Bearer token */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // 去掉 "Bearer " 前缀
        }
        return null;
    }
}
