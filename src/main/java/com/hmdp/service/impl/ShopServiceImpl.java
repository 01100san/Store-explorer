package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sun.reflect.generics.tree.VoidDescriptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        // Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 解决缓存击穿
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 声明无效key解决缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        // Shop shop = queryWithLogicalExpire(id);

        if (shop == null){
            return Result.fail("店铺不存在");
        }
        // 返回
        return Result.ok(shop);
    }

    // 创建线程池 =》 取出线程进行缓存的重建
    //private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     * @param id
     * @return
     */
    /*public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 从 Redis 中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // -- 不存在，直接返回 => 说明 不是热点key，热点key一直存在
        if (StrUtil.isBlank(shopJson)){
            return null;
        }
        // 命中,将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断缓存是否过期
        // --未过期，直接返回商铺信息
        if (expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        // --过期，缓存重建
        // --获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // ----成功，开启独立线程，实现缓存重建
        if (isLock){
            // 再次判断缓存是否过期,未过期直接返回
            String shopJsonAlive = stringRedisTemplate.opsForValue().get(key);
            RedisData redisDataAlive = JSONUtil.toBean(shopJsonAlive, RedisData.class);
            Shop shopNew = JSONUtil.toBean((JSONObject) redisDataAlive.getData(), Shop.class);
            LocalDateTime expireTimeNew = redisDataAlive.getExpireTime();
            if (expireTimeNew.isAfter(LocalDateTime.now())){
                return shopNew;
            }
            // 过期重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    saveShop2Redis(id, 30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // ----失败，返回过期商铺信息
        return shop;
    }*/

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    /*public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 从 Redis 中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 存在，直接返回
        if (StrUtil.isNotBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值""
        if (shopJson != null){
            return null;
        }
        // 缓存重建
        // --获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // --失败，休眠并重新获取锁
            if (!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // --成功，缓存是否存在
            // -----存在,直接返回
            String shopJsonNew = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJsonNew)){
                return JSONUtil.toBean(shopJsonNew, Shop.class);
            }
            // ----不存在，查询数据库
            shop = getById(id);
            // 模拟缓存重建延时
            Thread.sleep(200);
            // -- 不存在，返回错误
            if (shop == null){
                // 将空值写入 redis -- 缓存无效key，访问该key时，直接通过缓存返回，避免缓存穿透，访问数据库
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // -- 存在，写入 Redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放互斥锁
            unlock(lockKey);
        }
        // 返回
        return shop;
    }*/

    /**
     * 缓存无效key,解决缓存穿透
     * @param id
     * @return
     */
    /*public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 从 Redis 中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 存在，直接返回
        if (StrUtil.isNotBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值
        if (shopJson != null){
            return null;
        }

        // 不存在，查询数据库 shopJson == null
        Shop shop = getById(id);
        // -- 不存在，返回错误
        if (shop == null){
            // 将空值写入 redis -- 缓存无效key，访问该key时，直接通过缓存返回，避免缓存穿透，访问数据库
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // -- 存在，写入 Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 返回
        return shop;
    }*/

    /**
     * 缓存重建
     * @param id
     * @param expireSeconds
     */
    /*public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }*/

    /**
     * 获取锁
     * @param key
     * @return
     */
    /*private boolean tryLock(String key){
        // setIfAbsent => setnx key value ex ,
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }*/

    /**
     * 释放锁
     */
    /*private void unlock(String key){
        stringRedisTemplate.delete(key);
    }*/

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为空");
        }
        //1. 更新数据库
        updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /*
    // 想通过 hash 的方式存储商铺信息
    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从 Redis 中查询商铺缓存
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);
        log.info("商铺信息：{}",shopMap);
        // 存在
        if (!shopMap.isEmpty()){
            Shop shop = BeanUtil.mapToBean(shopMap, Shop.class, true, CopyOptions.create());

            return Result.ok(shop);
        }
        // 不存在，查询数据库
        Shop shop = getById(id);
        // -- 不存在
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        // -- 存在，写入 Redis
        Map<String, Object> map = BeanUtil.beanToMap(shop, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue +"" ));

        stringRedisTemplate.opsForHash().putAll(key, map);
        // 返回
        return Result.ok(shop);
    }*/


}
