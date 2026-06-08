package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AdminAgentTraceLogService;
import com.job.common.dto.agent.AgentTraceLogQueryDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentTraceLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者:hfj
 * 功能:后台 Agent Trace 日志接口
 * 说明:
 * 1. 该接口面向后台管理员。
 * 2. 主要用于排查 Agent 对话、工具调用、模型异常等问题。
 * 日期: 2026/6/8 20:06
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/logs")
public class AdminAgentTraceLogController {

    private final AdminAgentTraceLogService adminAgentTraceLogService;

    /**
     * 分页查询 Agent Trace 日志。
     *
     * @param query 查询条件
     * @return 分页日志
     */
    @GetMapping("/page")
    public Result<IPage<AgentTraceLogVO>> pageLogs(AgentTraceLogQueryDTO query) {
        IPage<AgentTraceLogVO> page = adminAgentTraceLogService.pageLogs(query);

        return Result.build(page, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询 Agent Trace 日志详情。
     *
     * @param id 日志ID
     * @return 日志详情
     */
    @GetMapping("/{id}")
    public Result<AgentTraceLogVO> detail(@PathVariable Long id) {
        AgentTraceLogVO detail = adminAgentTraceLogService.getDetail(id);

        return Result.build(detail, ResultCodeEnum.SUCCESS);
    }
}
