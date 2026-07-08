package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentFollowUpApplicationQueryDTO;
import com.job.common.dto.agent.AgentFollowUpRuleQueryDTO;
import com.job.common.dto.agent.AgentFollowUpRuleSaveDTO;
import com.job.common.vo.agent.AgentFollowUpApplicationVO;
import com.job.common.vo.agent.AgentFollowUpRuleVO;

/**
 * 后台求职跟进 Agent 服务。
 *
 * <p>核心职责：为管理员提供求职跟进规则的配置管理能力，以及跟进应用实例的查询与扫描触发能力。</p>
 *
 * <p>所属业务模块：求职跟进 / 后台规则管理</p>
 *
 * <p>主要调用链：Admin Controller → AdminFollowUpAgentService → 跟进规则领域 Service / Mapper / 扫描执行器</p>
 */
public interface AdminFollowUpAgentService {

    /**
     * 分页查询求职跟进应用实例列表。
     *
     * @param query 查询条件（包含用户 ID、申请状态、跟进阶段等过滤条件）
     * @return 跟进应用实例分页结果
     */
    IPage<AgentFollowUpApplicationVO> pageApplications(AgentFollowUpApplicationQueryDTO query);

    /**
     * 分页查询求职跟进规则列表。
     *
     * @param query 查询条件（包含规则名称、触发事件、启用状态等过滤条件）
     * @return 跟进规则分页结果
     */
    IPage<AgentFollowUpRuleVO> pageRules(AgentFollowUpRuleQueryDTO query);

    /**
     * 创建新的求职跟进规则。
     *
     * @param request 规则保存参数（包含触发条件、执行动作、优先级等）
     * @return 创建后的规则详情
     */
    AgentFollowUpRuleVO createRule(AgentFollowUpRuleSaveDTO request);

    /**
     * 更新指定求职跟进规则。
     *
     * @param id      规则 ID
     * @param request 规则保存参数
     * @return 更新后的规则详情
     */
    AgentFollowUpRuleVO updateRule(Long id, AgentFollowUpRuleSaveDTO request);

    /**
     * 删除指定求职跟进规则。
     *
     * @param id 规则 ID
     */
    void deleteRule(Long id);

    /**
     * 扫描全部启用的跟进规则并触发符合条件的执行实例。
     *
     * @return 本次扫描触发的新增跟进实例数量
     */
    int scanEnabledRules();
}
