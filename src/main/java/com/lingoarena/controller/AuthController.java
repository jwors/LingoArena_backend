package com.lingoarena.controller;

import com.lingoarena.dto.request.LoginRequest;
import com.lingoarena.dto.request.RefreshTokenRequest;
import com.lingoarena.dto.request.RegisterRequest;
import com.lingoarena.dto.response.AuthResponse;
import com.lingoarena.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器。
 *
 * @RestController = @Controller + @ResponseBody（所有返回值自动转 JSON）
 * @RequestMapping("/api/auth") 这个控制器中的所有接口都以 /api/auth 开头
 *
 * @AuthenticationPrincipal Long userId 从 JWT token 中解析出的用户 ID，
 * 由 JwtAuthFilter 在 SecurityContext 中设置的。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册 */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("register_info:{}", request);
        return ResponseEntity.ok(authService.register(request));
    }

    /** 登录 */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("login_info:{}",request);
        return ResponseEntity.ok(authService.login(request));
    }

    /** 刷新 token */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    /** 登出 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> me(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }
}
