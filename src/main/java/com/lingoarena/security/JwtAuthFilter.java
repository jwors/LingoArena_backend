package com.lingoarena.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingoarena.dto.response.ErrorResponse;
import com.lingoarena.exception.ErrorCode;
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
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            if (jwtTokenService.validateToken(token)) {
                Long userId = jwtTokenService.getUserIdFromToken(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // token 无效或过期，返回 401 + 错误码，前端可以根据 TOKEN_EXPIRED 跳登录页
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                objectMapper.writeValue(response.getWriter(),
                        new ErrorResponse(ErrorCode.TOKEN_EXPIRED.getCode(), ErrorCode.TOKEN_EXPIRED.getMessage()));
                return;
            }
        }

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
