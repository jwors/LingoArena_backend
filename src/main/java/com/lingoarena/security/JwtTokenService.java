package com.lingoarena.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 令牌服务。
 *
 * 负责：
 * - 生成 access token（短期，15 分钟）
 * - 生成 refresh token（长期，7 天）
 * - 验证 token 是否有效
 * - 从 token 中提取用户 ID
 *
 * jjwt 库说明：
 * - jjwt-api：接口定义（编译时需要）
 * - jjwt-impl：实现（运行时需要）
 * - jjwt-jackson：JSON 处理（运行时需要）
 * 三个依赖缺一不可。
 */
@Component
public class JwtTokenService {

    /** HMAC-SHA 密钥，从 application.yml 的 jwt.secret 读取 */
    private final SecretKey secretKey;
    /** access token 有效期（分钟） */
    private final long accessTokenExpirationMinutes;
    /** refresh token 有效期（天） */
    private final long refreshTokenExpirationDays;

    /**
     * 构造器注入配置值。
     * @Value 从 application.yml 读取配置，如果配置不存在会报错
     */
    public JwtTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-minutes}") long accessMinutes,
            @Value("${jwt.refresh-token-expiration-days}") long refreshDays) {
        // BASE64 解码后生成 HMAC-SHA 密钥
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpirationMinutes = accessMinutes;
        this.refreshTokenExpirationDays = refreshDays;
    }

    /** 生成 access token（短期），用户 ID 存在 subject 字段中 */
    public String generateAccessToken(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpirationMinutes * 60 * 1000))
                .signWith(secretKey)
                .compact();
    }

    /** 生成 refresh token（长期），用于无感刷新 access token */
    public String generateRefreshToken(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshTokenExpirationDays * 24 * 60 * 60 * 1000))
                .signWith(secretKey)
                .compact();
    }

    /** 从 token 中解析出用户 ID */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    /** 验证 token 是否有效（签名正确 + 未过期） */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
