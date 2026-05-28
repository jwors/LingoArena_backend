package com.lingoarena.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类。
 *
 * RedisTemplate 是 Spring Data Redis 的核心类，类似于 JdbcTemplate。
 * 配置了 Key 和 Value 的序列化方式：
 * - Key: String 序列化（方便在 redis-cli 中查看）
 * - Value: JSON 序列化（存对象自动转 JSON，取出来自动转回对象）
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 自定义 ObjectMapper，和 JacksonConfig 保持一致：下划线命名 + UTC 时区
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())  // 支持 LocalDateTime 序列化
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }
}
