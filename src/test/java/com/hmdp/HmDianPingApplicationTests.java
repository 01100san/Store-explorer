package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    /**
     * 测试将 逻辑过期
     */
    @Test
    public void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        // 以逻辑过期的方式存入 redis
        cacheClient.setLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1,shop, 10L, TimeUnit.SECONDS );
    }
}
