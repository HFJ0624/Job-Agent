package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentPlanQueryDTO;
import com.job.common.vo.agent.AgentPlanVO;

/**
 * 作者:hfj
 * 功能:后台 Agent 计划查询服务
 * 日期:2026/6/19
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
