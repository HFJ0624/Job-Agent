package com.job.bootstrap.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 作者:hfj
 * 功能:Sa-Token 登录拦截配置
 * 日期:2026/6/2 10:45
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册后端接口拦截器。
     *
     * @param registry SpringMVC 拦截器注册对象
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 前台接口走 /front/**，后台接口走 /admin/**，默认都要登录后才能访问。
        registry.addInterceptor(new SaInterceptor(handle -> {
            /*
             * SSE / 异步请求结束后，Servlet 容器会做一次 ASYNC 回派，拦截器会再执行一次；
             * 此时原请求的 Sa-Token 上下文已销毁，checkLogin 会误报“未登录”。
             * 只在首次请求派发时鉴权；ERROR 派发同样跳过（避免把 JSON 写进已提交的流）。
             */
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            DispatcherType dispatcherType = attributes.getRequest().getDispatcherType();
            if (dispatcherType == DispatcherType.ASYNC || dispatcherType == DispatcherType.ERROR) {
                return;
            }
            StpUtil.checkLogin();
        }))
                .addPathPatterns("/front/**", "/admin/**")

                // 2. 前台登录注册、头像上传、后台登录和接口文档地址不需要登录。
                .excludePathPatterns(
                        "/front/auth/login",
                        "/front/auth/register",
                        "/front/job/page",
                        "/front/job/*",
                        "/front/file/avatar",
                        "/admin/auth/login",
                        "/doc.html",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/admin/agent/eval/run-all"
                );
    }
}
