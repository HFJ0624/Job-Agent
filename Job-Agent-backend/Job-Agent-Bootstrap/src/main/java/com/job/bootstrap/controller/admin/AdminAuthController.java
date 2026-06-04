package com.job.bootstrap.controller.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobUserService;
import com.job.common.dto.auth.LoginDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.user.JobUser;
import com.job.common.vo.auth.LoginVO;
import com.job.common.vo.user.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者:hfj
 * 功能:后台系统登录、退出和获取当前后台用户信息接口
 * 日期:2026/6/4 15:20
 */
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final JobUserService jobUserService;

    /**
     * 后台登录接口。
     * P表示参数描述，账号可以是用户名、手机号或邮箱。
     *
     * @param request 登录请求参数，包含账号和密码
     * @return 返回 token 名称、token 值和当前后台用户信息
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO request) {
        // 1. 后台登录也复用用户账号体系，账号密码校验统一交给 Service 处理。
        JobUser user = jobUserService.login(request.getAccount(), request.getPassword());

        // 2. 登录成功后生成 Sa-Token 登录态，后台前端后续请求需要带上这个 token。
        StpUtil.login(user.getId());

        // 3. 统一返回 token 和用户信息，方便后台 Pinia 保存当前登录人。
        LoginVO response = new LoginVO(
                StpUtil.getTokenName(),
                StpUtil.getTokenValue(),
                UserVO.from(user)
        );
        return Result.build(response, ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台退出登录接口。
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
     * 查询当前后台登录用户接口。
     *
     * @return 返回当前后台登录用户信息
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        // 1. 从 Sa-Token 中读取当前登录用户 ID。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 根据用户 ID 查询完整用户信息，再转成 VO 返回给后台页面。
        JobUser user = jobUserService.getUserRequired(userId);
        return Result.build(UserVO.from(user), ResultCodeEnum.SUCCESS);
    }
}
