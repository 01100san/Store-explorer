package com.drtd.service;

import com.drtd.dto.Result;
import com.drtd.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zhl
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryList();
}
