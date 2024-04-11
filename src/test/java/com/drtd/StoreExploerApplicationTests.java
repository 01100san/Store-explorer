package com.drtd;

import com.drtd.entity.Shop;
import com.drtd.service.impl.ShopServiceImpl;
import com.drtd.utils.CacheClient;
import com.drtd.utils.RedisConstants;
import com.drtd.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class StoreExploerApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(30);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    /**
     * 测试将 逻辑过期
     */
    @Test
    public void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        // 以逻辑过期的方式存入 redis
        cacheClient.setLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1,shop, 10L, TimeUnit.SECONDS );
    }

    @Test
    public void testLoadShopData(){
        /**
         * shop 包括 美食和 ktv
         */
        List<Shop> shops = shopService.list();
        Map<Long, List<Shop>> map = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        /*List<Shop> list1 = new ArrayList<>();
        List<Shop> list2 = new ArrayList<>();
        for (Shop shop : shops){
            if (shop.getTypeId() == 1){
                list1.add(shop);
            }else if (shop.getTypeId() == 2){
                list2.add(shop);
            }
        }
        map.put(1L, list1);
        map.put(2L, list2);*/
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            List<Shop> all = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(all.size());
            for (Shop shop : all) {
//                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }

    }
}
