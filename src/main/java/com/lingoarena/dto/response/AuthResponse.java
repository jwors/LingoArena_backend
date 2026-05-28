package com.lingoarena.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 认证响应 DTO。
 * 登录/注册成功后返回 access token、refresh token 和用户基本信息。
 *
 * DTO 和 Entity 的区别：
 * Entity 对应数据库表结构，包含所有字段（如 password_hash）。
 * DTO 是网络传输对象，只包含需要暴露给客户端的数据。
 * 用 @Builder 创建对象：AuthResponse.builder().accessToken(...).build()
 */
@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    /** JWT access token（短期，15 分钟有效） */
    private String accessToken;
    /** Refresh token（长期，7 天有效，用于无感刷新） */
    private String refreshToken;
    /** 用户基本信息 */
    private UserInfo user;

    @Data
    @Builder
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String email;
        private String nickname;
    }
}
