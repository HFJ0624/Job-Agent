package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.JobUserService;
import com.job.common.dto.user.UserPageDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.user.JobUser;
import com.job.common.vo.user.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者:hfj
 * 功能:后台用户管理接口，提供用户分页查询和用户详情查询
 * 日期:2026/6/4 15:20
 */
@Validated
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final JobUserService jobUserService;

    /**
     * 分页查询用户列表。
     * P表示参数描述，keyword 可以按用户名、昵称、手机号、邮箱模糊搜索。
     *
     * @param request 分页查询参数，包含 pageNo、pageSize 和 keyword
     * @return 返回用户分页数据
     */
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(@Valid UserPageDTO request) {
        // 1. 查询数据库分页对象，后台列表页面需要总数、页码和当前页数据。
        IPage<JobUser> userPage = jobUserService.pageUsers(
                request.getPageNo(),
                request.getPageSize(),
                request.getKeyword()
        );

        // 2. 将实体对象转成 VO，避免把 password 等敏感字段返回给前端。
        List<UserVO> records = userPage.getRecords()
                .stream()
                .map(UserVO::from)
                .toList();

        // 3. 组装前端分页组件需要的数据结构。
        PageResult<UserVO> pageResult = new PageResult<>(
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
    public Result<UserVO> detail(@PathVariable Long id) {
        // 1. 查询用户详情，不存在时由 Service 抛出业务异常。
        JobUser user = jobUserService.getUserRequired(id);
        return Result.build(UserVO.from(user), ResultCodeEnum.SUCCESS);
    }
}
