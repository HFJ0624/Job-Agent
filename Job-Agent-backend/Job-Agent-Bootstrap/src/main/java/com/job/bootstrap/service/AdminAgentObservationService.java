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
 * 作者: hfj
 * 功能: 后台 Agent 统一观测查询服务
 * 日期: 2026/6/22
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

    IPage<AgentObservationAlertRuleVO> pageAlertRules(AgentObservationAlertRuleQueryDTO query);

    AgentObservationAlertRuleVO createAlertRule(AgentObservationAlertRuleSaveDTO request);

    AgentObservationAlertRuleVO updateAlertRule(Long id, AgentObservationAlertRuleSaveDTO request);

    void deleteAlertRule(Long id);

    List<AgentObservationAlertRecordVO> evaluateAlertRules();

    IPage<AgentObservationAlertRecordVO> pageAlertRecords(AgentObservationAlertRecordQueryDTO query);

    AgentObservationAlertRecordVO updateAlertRecordStatus(Long id, String status);

    List<AgentTraceRetentionPolicyVO> listRetentionPolicies();

    AgentTraceRetentionPolicyVO createRetentionPolicy(AgentTraceRetentionPolicySaveDTO request);

    AgentTraceRetentionPolicyVO updateRetentionPolicy(Long id, AgentTraceRetentionPolicySaveDTO request);

    AgentTraceRetentionPreviewVO previewRetentionPolicy(Long id);

    AgentTraceRetentionPreviewVO executeRetentionPolicy(Long id);
}
