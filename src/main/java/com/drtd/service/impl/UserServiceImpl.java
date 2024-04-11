package com.drtd.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.drtd.dto.LoginFormDTO;
import com.drtd.dto.Result;
import com.drtd.dto.UserDTO;
import com.drtd.entity.User;
import com.drtd.mapper.UserMapper;
import com.drtd.service.IUserService;
import com.drtd.utils.RegexUtils;
import com.drtd.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.drtd.utils.RedisConstants.*;
import static com.drtd.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author zhl
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private UserMapper userMapper;
    // 使用 String 结构保存验证码
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        boolean invalid = RegexUtils.isPhoneInvalid(phone);
        if (invalid){
            // 返回错误信息
            return Result.fail("手机号输入错误");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(4);
        // 保存验证码到 redis, 过期时间为 2min => set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码
        log.info("发送验证码成功，验证码：{}", code);
        // 返回 ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 再次校验手机号
        String phone = loginForm.getPhone();
        boolean invalid = RegexUtils.isPhoneInvalid(phone);
        if (invalid) {
            // 返回错误信息
            return Result.fail("手机号输入错误");
        }
        // 校验验证码是否过期，或是否正确
        // 从 redis 中获取
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String userCode = loginForm.getCode();
        // 不一致
        if (code == null || !code.equals(userCode)){
            return  Result.fail("验证码错误");
        }
        // 一致
        User user = query().eq("phone", phone).one();
        // 用户不存在
        if (user == null){
            // -- 创建用户
            user = createUserWithPhone(phone);
        }
        // 保存用户到 redis
        // -- 生成 token ，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // -- 将 User 对象 转为 HashMap 存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // -- 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // -- 设置 token 的有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 返回 token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = SIGN_KEY + userId + keySuffix;
        // 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
