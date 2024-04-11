package com.drtd.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ClassName: RedisConfigProperties
 * Package: com.hmdp.utils
 * Description
 *
 * @Author zhl
 * @Create 2024-04-08 10:22
 * version 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.redis")
public class RedisConfigProperties {
    private String host;
    private String port;
    private String password;
}
