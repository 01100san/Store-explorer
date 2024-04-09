package com.hmdp.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName: MessageConvert
 * Package: com.hmdp.config
 * Description
 *  定义消息转换器，将 需要发送的消息对象 转成 json
 * @Author zhl
 * @Create 2024-04-09 18:54
 * version 1.0
 */
@Configuration
public class MessageConvert {
    @Bean
    public MessageConverter messageConverter(){
        return new Jackson2JsonMessageConverter();
    }
}
