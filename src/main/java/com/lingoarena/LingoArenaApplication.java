package com.lingoarena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LingoArena 后端入口。
 *
 * @SpringBootApplication 是 Spring Boot 的核心注解，包含自动配置、组件扫描等功能
 * @EnableJpaAuditing    启用 JPA 自动审计（自动填充 created_at / updated_at 等字段）
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class LingoArenaApplication {

    /**
     * 启动入口。SpringApplication.run() 会启动内嵌 Tomcat 并加载整个应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(LingoArenaApplication.class, args);
    }
}
