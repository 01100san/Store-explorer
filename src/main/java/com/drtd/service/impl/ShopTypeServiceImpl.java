package com.drtd.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.drtd.dto.Result;
import com.drtd.entity.ShopType;
import com.drtd.mapper.ShopTypeMapper;
import com.drtd.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import static com.drtd.utils.RedisConstants.CACHE_SHOP_TYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zhl
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
