package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * ClassName: LoginInterceptor
 * Package: com.hmdp.utils
 * Description
 *
 * @Author zhl
 * @Create 2024/3/22 11:02
 * version 1.0
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取session 中 用户信息
        HttpSession session = request.getSession();
        UserDTO user = (UserDTO) session.getAttribute("user");
        // 不存在拦截
        if (user == null){
            response.setStatus(401);
            return false;
        }
        // 存在保存用户信息到 ThreadLocal
        UserHolder.saveUser((UserDTO)user);
        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
