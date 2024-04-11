package com.drtd;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.drtd.mapper")
@SpringBootApplication
public class StoreExplorerApplication {
    public static void main(String[] args) {
        SpringApplication.run(StoreExplorerApplication.class, args);
    }
}
