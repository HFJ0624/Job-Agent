package com.job.bootstrap.controller.admin;

import com.job.bootstrap.service.AgentEvalService;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 作者: hfj
 * 功能: Agent 评测管理接口
 *
 * 说明:
 * 1. 这个接口建议只给管理员使用。
 * 2. 不建议开放给普通用户。
 * 3. 后面可以加 Sa-Token 权限注解，例如 @SaCheckRole("admin")。
 */
@Tag(name = "Agent评测管理接口")
@RestController
@RequestMapping("/admin/agent/eval")
@RequiredArgsConstructor
public class AdminAgentEvalController {

    private final AgentEvalService agentEvalService;

    /**
     * 运行单条 Agent 评测用例。
     * @param caseId 评测用例ID
     * @return true 表示通过，false 表示失败
     */
    @PostMapping("/run/{caseId}")
    public Result<Boolean> runCase(@PathVariable Long caseId) {
        Boolean pass = agentEvalService.runCase(caseId);

        return Result.build(pass, ResultCodeEnum.SUCCESS
        );
    }

    /**
     * 运行所有启用状态的 Agent 评测用例。
     * @return 通过的用例数量
     */
    @PostMapping("/run-all")
    public Result<Integer> runAll() {
        Integer passCount = agentEvalService.runAllEnabledCases();

        return Result.build(passCount, ResultCodeEnum.SUCCESS);
    }
}
