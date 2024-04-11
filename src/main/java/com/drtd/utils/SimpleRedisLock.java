package com.drtd.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * ClassName: SimpleRedisLock
 * Package: com.hmdp.utils
 * Description
 *
 * @Author zhl
 * @Create 2024-04-07 21:46
 * version 1.0
 */
public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    /**
     * 让 lua 脚本在类加载时加载
     */
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("FreeLock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * key => lock:[name] value => [threadId]
     *             [name] => [order:userId]
     * @param timeoutSec    锁的过期时间
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        /**
         * 实现线程标识
         * 使用 UUID+threadId
         */
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean isSuccess = stringRedisTemplate.opsForValue().
                setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isSuccess);  // 避免空指针异常（包装类）
    }

    /**
     * Redis 支持lua 脚本功能，在一个脚本中编写多条Redis命令，确保命令执行时的原子性
     */
    @Override
    public void unlock() {
        // 调用 lua 脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

    /**
     * 不具有原子性判断是否是当前锁标识和删除锁是两个操作
     */
    /*@Override
    public void unlock() {
        // 获取想要释放锁的线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 从 Redis 获取锁的线程标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 判断线程标识是否一致
        if (threadId.equals(id)) {
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
