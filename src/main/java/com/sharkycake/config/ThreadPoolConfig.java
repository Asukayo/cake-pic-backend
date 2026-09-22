package com.sharkycake.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        // 自定义线程工厂，方便在日志中追踪线程
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "cake-pic-thread-" + threadNumber.getAndIncrement());
                return t;
            }
        };

        // 根据实际需求调整核心参数
        return new ThreadPoolExecutor(
                4,                      // 核心线程数
                10,                     // 最大线程数
                60L,                    // 线程空闲存活时间
                TimeUnit.SECONDS,       // 时间单位
                new ArrayBlockingQueue<>(100), // 阻塞队列容量
                threadFactory,          // 线程工厂
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程处理
        );
    }
}
