package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AdminFollowUpAgentService;
import com.job.common.dto.agent.AgentFollowUpApplicationQueryDTO;
import com.job.common.dto.agent.AgentFollowUpRuleQueryDTO;
import com.job.common.dto.agent.AgentFollowUpRuleSaveDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentFollowUpApplicationVO;
import com.job.common.vo.agent.AgentFollowUpRuleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台求职跟进 Agent 管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/follow-up")
public class AdminFollowUpAgentController {

    private final AdminFollowUpAgentService adminFollowUpAgentService;

    /**
     * 分页查询求职跟进明细。
     */
    @GetMapping("/applications/page")
    public Result<IPage<AgentFollowUpApplicationVO>> pageApplications(AgentFollowUpApplicationQueryDTO query) {
        return Result.build(adminFollowUpAgentService.pageApplications(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 分页查询自动跟进规则。
     */
    @GetMapping("/rules/page")
    public Result<IPage<AgentFollowUpRuleVO>> pageRules(AgentFollowUpRuleQueryDTO query) {
        return Result.build(adminFollowUpAgentService.pageRules(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增自动跟进规则。
     */
    @PostMapping("/rules")
    public Result<AgentFollowUpRuleVO> createRule(@Valid @RequestBody AgentFollowUpRuleSaveDTO request) {
        return Result.build(adminFollowUpAgentService.createRule(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改自动跟进规则。
     */
    @PutMapping("/rules/{id}")
    public Result<AgentFollowUpRuleVO> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody AgentFollowUpRuleSaveDTO request
    ) {
        return Result.build(adminFollowUpAgentService.updateRule(id, request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除自动跟进规则。
     */
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        adminFollowUpAgentService.deleteRule(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 手动触发规则扫描，方便后台测试规则是否能创建提醒。
     */
    @PostMapping("/rules/scan")
    public Result<Integer> scanRules() {
        return Result.build(adminFollowUpAgentService.scanEnabledRules(), ResultCodeEnum.SUCCESS);
    }
}
