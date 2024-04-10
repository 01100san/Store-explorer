package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 使用Redis 中的 sorted_set 集合进行滚动分页查询
 * zrevrangebyscore key max min withscores limit offset count
 *                     |score(时间戳)|
 *                    max = minTime（上一次分页）
 */
@Data
public class ScrollResult {
    private List<?> list;
    // 获取每次集合最小的分数（时间戳）
    private Long minTime;
    /**
     * offset偏移量是总集合中所有重复score的次数
     */
    private Integer offset;
}
