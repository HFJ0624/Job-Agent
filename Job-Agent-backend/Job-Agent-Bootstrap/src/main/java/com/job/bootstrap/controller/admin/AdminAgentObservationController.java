package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AdminAgentObservationService;
import com.job.common.dto.agent.AgentObservationAlertRecordQueryDTO;
import com.job.common.dto.agent.AgentObservationAlertRuleQueryDTO;
import com.job.common.dto.agent.AgentObservationAlertRuleSaveDTO;
import com.job.common.dto.agent.AgentObservationDashboardQueryDTO;
import com.job.common.dto.agent.AgentObservationEventQueryDTO;
import com.job.common.dto.agent.AgentTraceRetentionPolicySaveDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentObservationAlertRecordVO;
import com.job.common.vo.agent.AgentObservationAlertRuleVO;
import com.job.common.vo.agent.AgentObservationDashboardVO;
import com.job.common.vo.agent.AgentObservationEventVO;
import com.job.common.vo.agent.AgentObservationStatItemVO;
import com.job.common.vo.agent.AgentTraceRetentionPolicyVO;
import com.job.common.vo.agent.AgentTraceRetentionPreviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者: hfj
 * 功能: 后台 Agent 统一观测查询接口
 * 日期: 2026/6/22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/observations")
public class AdminAgentObservationController {

    private final AdminAgentObservationService adminAgentObservationService;

    /**
     * 分页查询 Agent 观测事件。
     *
     * @param query 查询条件
     * @return 观测事件分页
     */
    @GetMapping("/page")
    public Result<IPage<AgentObservationEventVO>> pageEvents(AgentObservationEventQueryDTO query) {
        return Result.build(adminAgentObservationService.pageEvents(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询 Agent 观测事件详情。
     *
     * @param id 事件 ID
     * @return 观测事件详情
     */
    @GetMapping("/{id}")
    public Result<AgentObservationEventVO> detail(@PathVariable Long id) {
        return Result.build(adminAgentObservationService.getDetail(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询 Agent 观测看板。
     *
     * @param query 查询条件
     * @return 看板指标
     */
    @GetMapping("/dashboard")
    public Result<AgentObservationDashboardVO> dashboard(AgentObservationDashboardQueryDTO query) {
        return Result.build(adminAgentObservationService.dashboard(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询失败分类统计。
     *
     * @param query 查询条件
     * @return 失败分类统计
     */
    @GetMapping("/failure-stats")
    public Result<List<AgentObservationStatItemVO>> failureStats(AgentObservationDashboardQueryDTO query) {
        return Result.build(adminAgentObservationService.failureStats(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 分页查询告警规则。
     *
     * @param query 查询条件
     * @return 告警规则分页
     */
    @GetMapping("/alert-rules/page")
    public Result<IPage<AgentObservationAlertRuleVO>> pageAlertRules(AgentObservationAlertRuleQueryDTO query) {
        return Result.build(adminAgentObservationService.pageAlertRules(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增告警规则。
     *
     * @param request 告警规则表单
     * @return 新增后的告警规则
     */
    @PostMapping("/alert-rules")
    public Result<AgentObservationAlertRuleVO> createAlertRule(
            @Valid @RequestBody AgentObservationAlertRuleSaveDTO request
    ) {
        return Result.build(adminAgentObservationService.createAlertRule(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改告警规则。
     *
     * @param id 规则 ID
     * @param request 告警规则表单
     * @return 修改后的告警规则
     */
    @PutMapping("/alert-rules/{id}")
    public Result<AgentObservationAlertRuleVO> updateAlertRule(
            @PathVariable Long id,
            @Valid @RequestBody AgentObservationAlertRuleSaveDTO request
    ) {
        return Result.build(adminAgentObservationService.updateAlertRule(id, request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除告警规则。
     *
     * @param id 规则 ID
     * @return 空结果
     */
    @DeleteMapping("/alert-rules/{id}")
    public Result<Void> deleteAlertRule(@PathVariable Long id) {
        adminAgentObservationService.deleteAlertRule(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 手动评估告警规则。
     *
     * @return 本次新增的告警记录
     */
    @PostMapping("/alert-rules/evaluate")
    public Result<List<AgentObservationAlertRecordVO>> evaluateAlertRules() {
        return Result.build(adminAgentObservationService.evaluateAlertRules(), ResultCodeEnum.SUCCESS);
    }

    /**
     * 分页查询告警记录。
     *
     * @param query 查询条件
     * @return 告警记录分页
     */
    @GetMapping("/alert-records/page")
    public Result<IPage<AgentObservationAlertRecordVO>> pageAlertRecords(AgentObservationAlertRecordQueryDTO query) {
        return Result.build(adminAgentObservationService.pageAlertRecords(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改告警记录状态。
     *
     * @param id 告警记录 ID
     * @param status 状态 OPEN/RESOLVED/IGNORED
     * @return 修改后的告警记录
     */
    @PutMapping("/alert-records/{id}/status")
    public Result<AgentObservationAlertRecordVO> updateAlertRecordStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        return Result.build(adminAgentObservationService.updateAlertRecordStatus(id, status), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询 Trace 保留策略列表。
     *
     * @return 保留策略列表
     */
    @GetMapping("/retention-policies")
    public Result<List<AgentTraceRetentionPolicyVO>> listRetentionPolicies() {
        return Result.build(adminAgentObservationService.listRetentionPolicies(), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增 Trace 保留策略。
     *
     * @param request 保留策略表单
     * @return 新增后的保留策略
     */
    @PostMapping("/retention-policies")
    public Result<AgentTraceRetentionPolicyVO> createRetentionPolicy(
            @Valid @RequestBody AgentTraceRetentionPolicySaveDTO request
    ) {
        return Result.build(adminAgentObservationService.createRetentionPolicy(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改 Trace 保留策略。
     *
     * @param id 策略 ID
     * @param request 保留策略表单
     * @return 修改后的保留策略
     */
    @PutMapping("/retention-policies/{id}")
    public Result<AgentTraceRetentionPolicyVO> updateRetentionPolicy(
            @PathVariable Long id,
            @Valid @RequestBody AgentTraceRetentionPolicySaveDTO request
    ) {
        return Result.build(adminAgentObservationService.updateRetentionPolicy(id, request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 预览 Trace 保留策略命中数据量。
     *
     * @param id 策略 ID
     * @return 预览结果
     */
    @GetMapping("/retention-policies/{id}/preview")
    public Result<AgentTraceRetentionPreviewVO> previewRetentionPolicy(@PathVariable Long id) {
        return Result.build(adminAgentObservationService.previewRetentionPolicy(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 手动执行 Trace 保留策略。
     *
     * @param id 策略 ID
     * @return 执行后的预览结果
     */
    @PostMapping("/retention-policies/{id}/execute")
    public Result<AgentTraceRetentionPreviewVO> executeRetentionPolicy(@PathVariable Long id) {
        return Result.build(adminAgentObservationService.executeRetentionPolicy(id), ResultCodeEnum.SUCCESS);
    }
}
