package com.job.bootstrap.controller.admin;

import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者:hfj
 * 功能:后台 Agent 工具 Schema 查询接口
 * 日期:2026/6/20
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/tools/schemas")
public class AdminAgentToolSchemaController {

    private final AgentToolSchemaRegistry agentToolSchemaRegistry;

    /**
     * 查询全部工具 Schema。
     *
     * @return 工具 Schema 列表
     */
    @GetMapping
    public Result<List<AgentToolSchema>> listSchemas() {
        return Result.build(agentToolSchemaRegistry.listAll(), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询单个工具 Schema。
     *
     * @param toolName 工具名，例如 GreetingGenerateTool.generateGreeting
     * @return 工具 Schema
     */
    @GetMapping("/detail")
    public Result<AgentToolSchema> detail(@RequestParam String toolName) {
        return Result.build(agentToolSchemaRegistry.getRequired(toolName), ResultCodeEnum.SUCCESS);
    }
}
