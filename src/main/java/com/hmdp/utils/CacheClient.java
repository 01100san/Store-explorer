package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import javafx.beans.binding.ObjectExpression;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * ClassName: CacheClient
 * Package: com.hmdp.utils
 * Description
 *
 * @Author zhl
 * @Create 2024/3/23 15:30
 * version 1.0
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 存入无效key，解决缓存穿透
     * @param keyPrefix
     * @param id
     * @param type  返回的返回值类型
     * @param dbFallback  数据库的操作过程
     * @param time 写入 redis 的过期时间
     * @param unit 写入 redis 的过期时间单位
     * @return
     * @param <R>   返回值类型
     * @param <ID>  根据数据库查询的id
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 从 Redis 中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 存在，直接返回
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null){
            return null;
        }

        // 不存在，查询数据库 json == null
        R r = dbFallback.apply(id);
        // -- 不存在，返回错误
        if (r == null){
            // 将空值写入 redis -- 缓存无效key，访问该key时，直接通过缓存返回，避免缓存穿透，访问数据库
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // -- 存在，写入 Redis
        this.set(key, r, time, unit);
        // 返回
        return r;
    }

    // 创建线程池 =》 取出线程进行缓存的重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 从 Redis 中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // -- 不存在，直接返回 => 说明 不是热点key，热点key一直存在
        if (StrUtil.isBlank(json)){
            return null;
        }
        // 命中,将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断缓存是否过期
        // --未过期，直接返回信息
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        // --过期，缓存重建
        // --获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // ----成功，开启独立线程，实现缓存重建
        if (isLock){
            // 再次判断缓存是否过期,未过期直接返回
            String jsonAlive = stringRedisTemplate.opsForValue().get(key);
            RedisData redisDataAlive = JSONUtil.toBean(jsonAlive, RedisData.class);
            R r1 = JSONUtil.toBean((JSONObject) redisDataAlive.getData(), type);
            LocalDateTime expireTimeNew = redisDataAlive.getExpireTime();
            if (expireTimeNew.isAfter(LocalDateTime.now())){
                return r1;
            }
            // 过期重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 查询数据库
                    R r2 = dbFallBack.apply(id);
                    // 写入 redis 以逻辑过期的方式
                    this.setLogicalExpire(key, r2, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // ----失败，返回过期商铺信息
        return r;
    }

    /**
     * 使用互斥锁解决缓存击穿
     */
    public <R,ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit){

        String key = keyPrefix + id;
        // 从 Redis 中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 存在，直接返回
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值""
        if (json != null){
            return null;
        }
        // 缓存重建
        // --获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // --失败，休眠并重新获取锁
            if (!isLock){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallBack, time, unit);
            }
            // --成功，缓存是否存在
            // -----存在,直接返回
            String jsonNew = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(jsonNew)){
                return JSONUtil.toBean(jsonNew, type);
            }
            // ----不存在，查询数据库
            r = dbFallBack.apply(id);
            // -- 不存在，返回错误
            if (r == null){
                // 将空值写入 redis -- 缓存无效key，访问该key时，直接通过缓存返回，避免缓存穿透，访问数据库
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // -- 存在，写入 Redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放互斥锁
            unlock(lockKey);
        }
        // 返回
        return r;
    }

    /**
     * 获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        // setIfAbsent => setnx key value ex ,
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    static class RedisData{
        private Object data;
        private LocalDateTime expireTime;

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public LocalDateTime getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(LocalDateTime expireTime) {
            this.expireTime = expireTime;
        }
    }
}
