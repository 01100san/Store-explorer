package com.drtd.config;

import com.drtd.utils.RedisConfigProperties;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName: Redisson
 * Package: com.hmdp.config
 * Description
 *
 * @Author zhl
 * @Create 2024-04-08 10:01
 * version 1.0
 */
@Configuration
public class RedissonConfig {
    @Autowired
    private RedisConfigProperties redisConfigProperties;

    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        // 设置单节点的redis
        config.useSingleServer()
                .setAddress("redis://" + redisConfigProperties.getHost() + ":" + redisConfigProperties.getPort())
                .setPassword(redisConfigProperties.getPassword());
        // 创建 Redisson 对象
        return org.redisson.Redisson.create(config);
    }
}
