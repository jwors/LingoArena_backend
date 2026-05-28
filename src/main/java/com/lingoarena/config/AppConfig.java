package com.lingoarena.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 应用通用配置。
 * 这里的 @Bean 方法会在 Spring 启动时执行，返回的对象会被 Spring 管理，
 * 其他地方可以通过 @Autowired 或构造器注入使用。
 */
@Configuration
public class AppConfig {

    /**
     * 游戏定时器线程池，用于处理每轮答题的 15 秒超时计时。
     * newScheduledThreadPool(4)：4 个线程的定时任务池，支持延迟执行和周期性执行。
     */
    @Bean
    public ScheduledExecutorService gameScheduler() {
        return Executors.newScheduledThreadPool(4);
    }
}
