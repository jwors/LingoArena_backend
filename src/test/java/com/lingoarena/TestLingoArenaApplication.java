package com.lingoarena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 测试配置类。
 *
 * 使用 Testcontainers 在测试时启动真实的 PostgreSQL 容器，
 * 而不是用 H2 内存数据库。这样能避免 H2 和 PostgreSQL 行为差异导致的问题。
 *
 * 测试时会自动启动一个 PostgreSQL Docker 容器，测试结束后自动销毁。
 */
@TestConfiguration(proxyBeanMethods = false)
@Testcontainers
public class TestLingoArenaApplication {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("lingoarena_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    public static void main(String[] args) {
        SpringApplication.from(LingoArenaApplication::main)
                .with(TestLingoArenaApplication.class)
                .run(args);
    }
}
