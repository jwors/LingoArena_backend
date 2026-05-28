package com.lingoarena.repository;

import com.lingoarena.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Refresh Token 数据访问层。
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    /** 根据 token 值查找记录 */
    Optional<RefreshToken> findByToken(String token);
    /** 删除某个用户的所有 refresh token（用于"全部登出"功能） */
    void deleteByUserId(Long userId);
}
