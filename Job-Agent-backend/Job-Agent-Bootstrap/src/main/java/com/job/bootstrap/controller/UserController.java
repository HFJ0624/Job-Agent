package com.job.bootstrap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.JobUserService;
import com.job.common.dto.user.UpdateUserProfileRequest;
import com.job.common.dto.user.UserPageRequest;
import com.job.common.dto.user.UserResponse;
import com.job.common.entity.base.PageResult;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.user.JobUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者:hfj
 * 功能:用户资料维护和用户分页查询接口
 * 日期:2026/6/2 10:45
 */
@Validated
@RestController
@RequestMapping("/api/user")
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
    public Result<UserResponse> updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        // 1. 通过 Sa-Token 获取当前登录用户 ID，确保只能修改自己的资料。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 调用 Service 完成资料校验和更新。
        JobUser user = jobUserService.updateProfile(userId, request.toEntity());
        return Result.build(UserResponse.from(user), ResultCodeEnum.SUCCESS);
    }

    /**
     * 分页查询用户列表。
     * P表示参数描述，keyword 可以按用户名、昵称、手机号、邮箱模糊搜索。
     *
     * @param request 分页查询参数，包含 pageNo、pageSize 和 keyword
     * @return 返回用户分页数据
     */
    @GetMapping("/page")
    public Result<PageResult<UserResponse>> page(@Valid UserPageRequest request) {
        // 1. 查询数据库分页对象。
        IPage<JobUser> userPage = jobUserService.pageUsers(
                request.getPageNo(),
                request.getPageSize(),
                request.getKeyword()
        );

        // 2. 将实体对象转成响应对象，避免把 password 返回给前端。
        List<UserResponse> records = userPage.getRecords()
                .stream()
                .map(UserResponse::from)
                .toList();

        // 3. 组装前端分页组件需要的数据结构。
        PageResult<UserResponse> pageResult = new PageResult<>(
                records,
                userPage.getTotal(),
                userPage.getCurrent(),
                userPage.getSize()
        );
        return Result.build(pageResult, ResultCodeEnum.SUCCESS);
    }

    /**
     * 根据用户 ID 查询用户详情。
     *
     * @param id 用户 ID
     * @return 返回指定用户信息
     */
    @GetMapping("/{id}")
    public Result<UserResponse> detail(@PathVariable Long id) {
        // 1. 查询用户详情，不存在时由 Service 抛出业务异常。
        JobUser user = jobUserService.getUserRequired(id);
        return Result.build(UserResponse.from(user), ResultCodeEnum.SUCCESS);
    }
}
