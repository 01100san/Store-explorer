package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UnknownFormatConversionException;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static java.util.Arrays.stream;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zhl
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        UserDTO user = UserHolder.getUser();
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            if (user != null){
                this.isBlogLiked(blog);
            }
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 查询 blog 的用户信息
        queryBlogUser(blog);
        // 查询 blog 是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 获取登录的用户
        Long userId = UserHolder.getUser().getId();
        // 判断当前用户（userId）是否被点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }


    /**
     * 使用Redis中的 zset 集合存储点赞详情，以及是否被点赞
     */
    @Override
    public Result likeBlog(Long id) {
        // 获取登录的用户
        Long userId = UserHolder.getUser().getId();
        // 判断当前用户（userId）是否被点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null){
            // 未点过赞 liked++
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 点赞成功保存到 zset集合 中
            if (isSuccess){
//                stringRedisTemplate.opsForSet().add(key, userId.toString());
                // 使用 zset 集合的方式，方便作为点赞排行榜 zadd key score member
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 已点过赞再次点赞 liked--
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 把用户从 zset 集合中移除
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询当前blog点赞前5的用户  zrange key 0 4
     * 把 String 类型 id 转成 Long 类型 id
     * 根据id查询用户
     * @param id    blogId
     * @return  该blog 被点赞的top5 用户
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 将 ids 中的 id 以 ，分隔 用字符串拼接
        String idStr = StrUtil.join(",", ids);
        // 数据中对点赞的排序有问题，主要是 in(,) 导致的id排序，现在需要添加按照 field 特定字段排序
        //  WHERE id IN ( 1 , 1011 ) ORDER BY FIELD(id,1011,1)
        List<UserDTO> userDTOS = userService
                .query().in("id",ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
