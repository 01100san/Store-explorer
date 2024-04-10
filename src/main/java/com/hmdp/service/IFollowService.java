package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zhl
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 判断是否关注还是取关
     * @param followUserId
     * @param isFollow  是否关注
     * @return
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询是否关注
     * @param followUserId
     * @return
     */
    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
