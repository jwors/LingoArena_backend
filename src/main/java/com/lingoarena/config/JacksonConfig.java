package com.lingoarena.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson JSON 序列化配置。
 *
 * 作用：Spring Boot 默认用 Jackson 做 JSON 序列化/反序列化。
 * 这里配置了：
 * - 字段命名策略：Java 的驼峰（userId）自动转为 JSON 的下划线（user_id）
 * - 时区：统一使用 UTC
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // 例如：Java 字段名 userId → JSON 中显示为 user_id
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            builder.timeZone("UTC");
        };
    }
}
