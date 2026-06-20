package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AdminAgentPlanService;
import com.job.common.dto.agent.AgentPlanQueryDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentPlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者:hfj
 * 功能:后台 Agent 计划查询接口
 * 日期:2026/6/19
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/plans")
public class AdminAgentPlanController {

    private final AdminAgentPlanService adminAgentPlanService;

    /**
     * 分页查询 Agent 计划。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<IPage<AgentPlanVO>> pagePlans(AgentPlanQueryDTO query) {
        return Result.build(adminAgentPlanService.pagePlans(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询 Agent 计划详情。
     *
     * @param id 计划ID
     * @return 计划详情
     */
    @GetMapping("/{id}")
    public Result<AgentPlanVO> detail(@PathVariable Long id) {
        return Result.build(adminAgentPlanService.getDetail(id), ResultCodeEnum.SUCCESS);
    }
}
