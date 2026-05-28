package com.lingoarena.repository;

import com.lingoarena.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层。
 * 继承 JpaRepository 自动获得 CRUD 方法（save, findById, findAll, delete 等）。
 * 自定义方法：Spring Data JPA 会根据方法名自动生成查询语句。
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /** 根据邮箱查找用户（登录时使用） */
    Optional<User> findByEmail(String email);
    /** 判断邮箱是否已被注册 */
    boolean existsByEmail(String email);
}
