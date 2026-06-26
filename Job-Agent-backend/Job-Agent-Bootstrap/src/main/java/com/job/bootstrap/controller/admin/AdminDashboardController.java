package com.job.bootstrap.controller.admin;

import com.job.bootstrap.service.AdminDashboardService;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.admin.AdminDashboardOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台首页看板接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * 查询后台首页聚合数据。
     *
     * @return 后台首页指标、待处理事项和系统状态
     */
    @GetMapping("/overview")
    public Result<AdminDashboardOverviewVO> overview() {
        return Result.build(adminDashboardService.getOverview(), ResultCodeEnum.SUCCESS);
    }
}
