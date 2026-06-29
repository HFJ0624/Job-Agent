package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentFollowUpApplicationQueryDTO;
import com.job.common.dto.agent.AgentFollowUpRuleQueryDTO;
import com.job.common.dto.agent.AgentFollowUpRuleSaveDTO;
import com.job.common.vo.agent.AgentFollowUpApplicationVO;
import com.job.common.vo.agent.AgentFollowUpRuleVO;

/**
 * 后台求职跟进 Agent 服务。
 */
public interface AdminFollowUpAgentService {

    IPage<AgentFollowUpApplicationVO> pageApplications(AgentFollowUpApplicationQueryDTO query);

    IPage<AgentFollowUpRuleVO> pageRules(AgentFollowUpRuleQueryDTO query);

    AgentFollowUpRuleVO createRule(AgentFollowUpRuleSaveDTO request);

    AgentFollowUpRuleVO updateRule(Long id, AgentFollowUpRuleSaveDTO request);

    void deleteRule(Long id);

    int scanEnabledRules();
}
