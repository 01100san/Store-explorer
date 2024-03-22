package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
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
        // 保存验证码到 redis

        // 保存验证码到 session
        session.setAttribute("code", code);
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
        String code = (String) session.getAttribute("code");
        String userCode = loginForm.getCode();
        // 不一致
        if (code == null || !code.equals(userCode)){
            return  Result.fail("验证码错误");
        }
        // 一致
        User user = query().eq("phone", phone).one();
        // 用户不存在
        if (user == null){
            // 创建用户
            user = createUserWithPhone(phone);
        }
        // 保存用户到 session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
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
