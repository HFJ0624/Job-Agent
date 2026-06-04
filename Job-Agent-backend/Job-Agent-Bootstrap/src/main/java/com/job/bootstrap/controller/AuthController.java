package com.job.bootstrap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobUserService;
import com.job.common.dto.auth.LoginRequest;
import com.job.common.dto.auth.LoginResponse;
import com.job.common.dto.auth.RegisterRequest;
import com.job.common.dto.user.UserResponse;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.user.JobUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者:hfj
 * 功能:用户登录、注册、退出和获取当前用户信息接口
 * 日期:2026/6/2 10:45
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JobUserService jobUserService;

    /**
     * 用户注册接口。
     * P表示参数描述，方便前端知道需要传什么。
     *
     * @param request 注册请求参数，包含用户名、密码、昵称、手机号和邮箱
     * @return 返回注册成功后的用户信息，不包含密码
     */
    @PostMapping("/register")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        // 1. 把前端 DTO 转成用户实体，并交给 Service 做注册校验和保存。
        JobUser user = jobUserService.register(request.toEntity());

        // 2. 统一包装成 Result 返回给前端。
        return Result.build(UserResponse.from(user), ResultCodeEnum.SUCCESS);
    }

    /**
     * 用户登录接口。
     * P表示参数描述，账号可以是用户名、手机号或邮箱。
     *
     * @param request 登录请求参数，包含账号和密码
     * @return 返回 token 名称、token 值和当前用户信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 1. 校验账号密码，Service 会负责密码比对和账号状态判断。
        JobUser user = jobUserService.login(request.getAccount(), request.getPassword());

        // 2. 登录成功后让 Sa-Token 生成登录态。
        StpUtil.login(user.getId());

        // 3. 把 token 和用户信息返回给前端，前端后续请求需要带上 token。
        LoginResponse response = new LoginResponse(
                StpUtil.getTokenName(),
                StpUtil.getTokenValue(),
                UserResponse.from(user)
        );
        return Result.build(response, ResultCodeEnum.SUCCESS);
    }

    /**
     * 用户退出登录接口。
     *
     * @return 返回退出成功结果
     */
    @PostMapping("/logout")
    public Result<Object> logout() {
        // 1. 清除当前请求对应的 Sa-Token 登录态。
        StpUtil.logout();
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询当前登录用户接口。
     *
     * @return 返回当前登录用户信息，不包含密码
     */
    @GetMapping("/me")
    public Result<UserResponse> me() {
        // 1. 从 Sa-Token 中取出当前登录用户 ID。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 根据用户 ID 查询用户详情。
        JobUser user = jobUserService.getUserRequired(userId);
        return Result.build(UserResponse.from(user), ResultCodeEnum.SUCCESS);
    }
}
