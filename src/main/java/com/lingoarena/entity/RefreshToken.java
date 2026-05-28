package com.lingoarena.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Refresh Token 实体，对应 refresh_tokens 表。
 *
 * 为什么需要这张表？
 * JWT refresh token 默认是无状态的，一旦签发无法撤销。
 * 把 refresh token 存到数据库，可以实现"登出所有设备"、
 * "检测 token 泄露"等安全功能。revoked 字段标记是否已撤销。
 */
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** refresh token 的字符串值（唯一） */
    @Column(nullable = false, unique = true, length = 500)
    private String token;

    /** 过期时间 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 是否已撤销（注销登录时标记为 true） */
    @Column(nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
