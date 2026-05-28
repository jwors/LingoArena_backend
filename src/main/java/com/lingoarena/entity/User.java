package com.lingoarena.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库 users 表。
 *
 * JPA 注解说明：
 * @Entity              标记这是一个 JPA 实体，会被 Hibernate 管理
 * @Table(name="users") 指定对应的数据库表名
 * @Id                  标记主键
 * @GeneratedValue      主键生成策略：IDENTITY = 使用数据库自增（BIGSERIAL）
 * @Column              指定列属性（是否为空、是否唯一等）
 *
 * Lombok 注解说明：
 * @Data                自动生成 getter/setter/toString/equals/hashCode
 * @Builder             提供建造者模式创建对象：User.builder().email(...).build()
 * @NoArgsConstructor    生成无参构造器（JPA 需要）
 * @AllArgsConstructor   生成全参构造器
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** 用户 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 邮箱（唯一，用作登录账号） */
    @Column(nullable = false, unique = true)
    private String email;

    /** 密码的 BCrypt 哈希值，不存明文 */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** 用户昵称（显示用） */
    @Column(nullable = false)
    private String nickname;

    /** 创建时间（由 @CreatedDate 自动填充） */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间（由 @LastModifiedDate 自动填充） */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
