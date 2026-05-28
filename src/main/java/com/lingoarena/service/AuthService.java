package com.lingoarena.service;

import com.lingoarena.dto.request.LoginRequest;
import com.lingoarena.dto.request.RegisterRequest;
import com.lingoarena.dto.response.AuthResponse;
import com.lingoarena.entity.RefreshToken;
import com.lingoarena.entity.User;
import com.lingoarena.exception.BusinessException;
import com.lingoarena.exception.ErrorCode;
import com.lingoarena.mapper.UserMapper;
import com.lingoarena.repository.RefreshTokenRepository;
import com.lingoarena.repository.UserRepository;
import com.lingoarena.security.JwtTokenService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务。
 *
 * 处理注册、登录、刷新 token、登出等用户认证相关业务逻辑。
 *
 * @Service 标记这是一个 Spring Bean，会自动被注入到需要它的地方。
 * @Transactional 标记的方法会运行在数据库事务中——要么全部成功，要么全部回滚。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;

    /** 用户注册 */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查邮箱是否已被注册
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS.getCode(),
                    ErrorCode.EMAIL_ALREADY_EXISTS.getMessage());
        }

        // 创建用户（密码用 BCrypt 加密，不存明文）
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();
        user = userRepository.save(user);

        return generateAuthResponse(user);
    }

    /** 用户登录 */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS.getCode(),
                        ErrorCode.INVALID_CREDENTIALS.getMessage()));

        // 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS.getCode(),
                    ErrorCode.INVALID_CREDENTIALS.getMessage());
        }

        return generateAuthResponse(user);
    }

    /** 刷新 token：用 refresh token 换取新的 access token + refresh token */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        // 验证 refresh token 是否有效
        if (!jwtTokenService.validateToken(refreshTokenValue)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN.getCode(),
                    ErrorCode.INVALID_TOKEN.getMessage());
        }

        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN.getCode(),
                        ErrorCode.INVALID_TOKEN.getMessage()));

        // 检查是否已撤销或过期
        if (storedToken.getRevoked() || storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED.getCode(),
                    ErrorCode.TOKEN_EXPIRED.getMessage());
        }

        // 撤销旧 token（安全措施：每次刷新都让旧 token 失效）
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        return generateAuthResponse(user);
    }

    /** 登出：撤销 refresh token */
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    /** 获取当前登录用户信息 */
    public AuthResponse.UserInfo getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));
        return userMapper.toUserInfo(user);
    }

    /** 生成认证响应（JWT + 用户信息） */
    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtTokenService.generateAccessToken(user.getId());
        String refreshToken = jwtTokenService.generateRefreshToken(user.getId());

        // 将 refresh token 存入数据库（支持撤销）
        RefreshToken tokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(tokenEntity);

        AuthResponse.UserInfo userInfo = userMapper.toUserInfo(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userInfo)
                .build();
    }
}
