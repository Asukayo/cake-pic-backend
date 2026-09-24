package com.sharkycake.infrastructure.cache;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@ConfigurationProperties(prefix = "spring.redis")
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.password}")
    private String password;

    @Bean
    public RedissonClient redisson() {
        Config config = new Config();
        config.useSingleServer().setAddress(String.format("redis://%s:%d", host, port))
                .setPassword(password);
        // 创建RedissonClient对象
        return Redisson.create(config);
    }


}
