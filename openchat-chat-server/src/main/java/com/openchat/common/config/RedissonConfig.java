package com.openchat.common.config;

import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description:
 * Author: <a  > TGH</a>
 * Date: 2023-04-22
 */
@Configuration
@RequiredArgsConstructor
public class RedissonConfig {
    final DataRedisProperties redisProperties;

    @Bean(destroyMethod = "shutdown") //服务停止销毁对象
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setPassword(redisProperties.getPassword());
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase());
        return Redisson.create(config);
    }
}
