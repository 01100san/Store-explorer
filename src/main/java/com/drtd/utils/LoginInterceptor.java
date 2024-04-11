package com.drtd.utils;

import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ClassName: LoginInterceptor
 * Package: com.drtd.utils
 * Description
 *  判断是否有用户
 * @Author zhl
 * @Create 2024/3/22 11:02
 * version 1.0
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断是否需要拦截 (ThreadLocal 中是否有用户)
        if (UserHolder.getUser() == null){
            response.setStatus(401);
            // 拦截
            return false;
        }
        // 有用户 放行
        return true;
    }
}
