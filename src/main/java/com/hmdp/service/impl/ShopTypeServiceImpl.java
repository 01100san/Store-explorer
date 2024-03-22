package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = CACHE_SHOP_TYPE_LIST;
        // 取出 Redis 中的商户分类
        String typeJson = stringRedisTemplate.opsForValue().get(key);
        // --存在
        if (StrUtil.isNotBlank(typeJson)) {
            List<ShopType> shopTypes = JSONUtil.toList(typeJson, ShopType.class);
            Long total = (long) shopTypes.size();
            return Result.ok(shopTypes, total);
        }
        // --不存在
        // 查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // -- 不存在
        if(CollectionUtil.isEmpty(typeList)){
            return Result.fail("商户类别未查询到");
        }
        // -- 存在，写入 Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));

        return Result.ok(typeList);
    }
}
