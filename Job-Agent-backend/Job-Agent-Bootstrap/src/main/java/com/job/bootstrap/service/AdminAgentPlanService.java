package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentPlanQueryDTO;
import com.job.common.vo.agent.AgentPlanVO;

/**
 * 后台 Agent 计划查询服务接口。
 *
 * <p>核心职责：为运营和研发人员提供 Agent 生成计划的分页查询和详情查看能力，支持问题排查和效果分析。</p>
 *
 * <p>所属业务模块：后台管理 - Agent 运维</p>
 *
 * <p>主要调用链：
 * AdminAgentPlanController -&gt; AdminAgentPlanService -&gt; AdminAgentPlanServiceImpl -&gt; AgentPlanRepository / AgentTraceLogRepository</p>
 */
public interface AdminAgentPlanService {

    /**
     * 分页查询 Agent 计划。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<AgentPlanVO> pagePlans(AgentPlanQueryDTO query);

    /**
     * 查询计划详情。
     *
     * @param id 计划ID
     * @return 计划详情
     */
    AgentPlanVO getDetail(Long id);
}
