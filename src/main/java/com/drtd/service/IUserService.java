package com.drtd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.drtd.dto.LoginFormDTO;
import com.drtd.dto.Result;
import com.drtd.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zhl
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
