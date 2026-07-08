package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentObservationAlertRecordQueryDTO;
import com.job.common.dto.agent.AgentObservationAlertRuleQueryDTO;
import com.job.common.dto.agent.AgentObservationAlertRuleSaveDTO;
import com.job.common.dto.agent.AgentObservationDashboardQueryDTO;
import com.job.common.dto.agent.AgentObservationEventQueryDTO;
import com.job.common.dto.agent.AgentTraceRetentionPolicySaveDTO;
import com.job.common.vo.agent.AgentObservationAlertRecordVO;
import com.job.common.vo.agent.AgentObservationAlertRuleVO;
import com.job.common.vo.agent.AgentObservationDashboardVO;
import com.job.common.vo.agent.AgentObservationEventVO;
import com.job.common.vo.agent.AgentObservationStatItemVO;
import com.job.common.vo.agent.AgentTraceRetentionPolicyVO;
import com.job.common.vo.agent.AgentTraceRetentionPreviewVO;

import java.util.List;

/**
 * 后台 Agent 统一观测查询服务接口。
 *
 * <p>核心职责：为运营和研发团队提供 Agent 运行时的全链路可观测能力，包括事件查询、看板统计、告警规则管理及 Trace 保留策略。</p>
 *
 * <p>所属业务模块：后台管理 - Agent 运维（Observability）</p>
 *
 * <p>主要调用链：
 * AdminAgentObservationController -&gt; AdminAgentObservationService -&gt; AdminAgentObservationServiceImpl -&gt; AgentObservationEventRepository / AlertRuleRepository</p>
 */
public interface AdminAgentObservationService {

    /**
     * 分页查询观测事件。
     *
     * @param query 查询条件
     * @return 观测事件分页
     */
    IPage<AgentObservationEventVO> pageEvents(AgentObservationEventQueryDTO query);

    /**
     * 查询观测事件详情。
     *
     * @param id 事件 ID
     * @return 观测事件详情
     */
    AgentObservationEventVO getDetail(Long id);

    /**
     * 查询 Agent 观测看板。
     *
     * @param query 查询条件
     * @return 看板指标
     */
    AgentObservationDashboardVO dashboard(AgentObservationDashboardQueryDTO query);

    /**
     * 查询失败分类统计。
     *
     * @param query 查询条件
     * @return 失败分类统计
     */
    List<AgentObservationStatItemVO> failureStats(AgentObservationDashboardQueryDTO query);

    /**
     * 分页查询告警规则。
     *
     * @param query 查询条件
     * @return 告警规则分页
     */
    IPage<AgentObservationAlertRuleVO> pageAlertRules(AgentObservationAlertRuleQueryDTO query);

    /**
     * 创建告警规则。
     *
     * @param request 告警规则保存参数
     * @return 创建后的告警规则
     */
    AgentObservationAlertRuleVO createAlertRule(AgentObservationAlertRuleSaveDTO request);

    /**
     * 更新告警规则。
     *
     * @param id 告警规则 ID
     * @param request 告警规则保存参数
     * @return 更新后的告警规则
     */
    AgentObservationAlertRuleVO updateAlertRule(Long id, AgentObservationAlertRuleSaveDTO request);

    /**
     * 删除告警规则。
     *
     * @param id 告警规则 ID
     */
    void deleteAlertRule(Long id);

    /**
     * 评估所有告警规则并生成告警记录。
     *
     * @return 本次评估产生的告警记录列表
     */
    List<AgentObservationAlertRecordVO> evaluateAlertRules();

    /**
     * 分页查询告警记录。
     *
     * @param query 查询条件
     * @return 告警记录分页
     */
    IPage<AgentObservationAlertRecordVO> pageAlertRecords(AgentObservationAlertRecordQueryDTO query);

    /**
     * 更新告警记录处理状态。
     *
     * @param id 告警记录 ID
     * @param status 目标状态
     * @return 更新后的告警记录
     */
    AgentObservationAlertRecordVO updateAlertRecordStatus(Long id, String status);

    /**
     * 查询所有 Trace 保留策略。
     *
     * @return 保留策略列表
     */
    List<AgentTraceRetentionPolicyVO> listRetentionPolicies();

    /**
     * 创建 Trace 保留策略。
     *
     * @param request 保留策略保存参数
     * @return 创建后的保留策略
     */
    AgentTraceRetentionPolicyVO createRetentionPolicy(AgentTraceRetentionPolicySaveDTO request);

    /**
     * 更新 Trace 保留策略。
     *
     * @param id 保留策略 ID
     * @param request 保留策略保存参数
     * @return 更新后的保留策略
     */
    AgentTraceRetentionPolicyVO updateRetentionPolicy(Long id, AgentTraceRetentionPolicySaveDTO request);

    /**
     * 预览保留策略执行效果。
     *
     * @param id 保留策略 ID
     * @return 预览结果，包含预计清理数量
     */
    AgentTraceRetentionPreviewVO previewRetentionPolicy(Long id);

    /**
     * 执行保留策略，清理过期 Trace 数据。
     *
     * @param id 保留策略 ID
     * @return 执行结果，包含实际清理数量
     */
    AgentTraceRetentionPreviewVO executeRetentionPolicy(Long id);
}
