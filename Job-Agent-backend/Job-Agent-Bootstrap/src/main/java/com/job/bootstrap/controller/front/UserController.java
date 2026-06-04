package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobUserService;
import com.job.common.dto.user.UpdateUserProfileDTO;
import com.job.common.vo.user.UserVO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.user.JobUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者:hfj
 * 功能:用户资料维护和用户分页查询接口
 * 日期:2026/6/2 10:45
 */
@Validated
@RestController
@RequestMapping("/front/user")
@RequiredArgsConstructor
public class UserController {

    private final JobUserService jobUserService;

    /**
     * 修改当前登录用户资料。
     * P表示参数描述，资料接口只允许修改个人信息，不允许改账号状态。
     *
     * @param request 用户资料表单参数
     * @return 返回修改后的用户信息
     */
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateUserProfileDTO request) {
        // 1. 通过 Sa-Token 获取当前登录用户 ID，确保只能修改自己的资料。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 调用 Service 完成资料校验和更新。
        JobUser user = jobUserService.updateProfile(userId, request.toEntity());
        return Result.build(UserVO.from(user), ResultCodeEnum.SUCCESS);
    }
}
