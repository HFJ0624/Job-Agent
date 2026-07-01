package com.job.bootstrap.controller.admin;

import com.job.bootstrap.service.AdminAgentOperationService;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentOperationDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Agent 运营看板接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/operations")
public class AdminAgentOperationController {

    private final AdminAgentOperationService adminAgentOperationService;

    /**
     * 查询 Agent 运营看板。
     */
    @GetMapping("/dashboard")
    public Result<AgentOperationDashboardVO> dashboard() {
        return Result.build(adminAgentOperationService.dashboard(), ResultCodeEnum.SUCCESS);
    }
}
