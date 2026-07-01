package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentActionItemQueryDTO;
import com.job.common.vo.agent.AgentActionItemVO;

/**
 * Admin Agent 行动项管理服务。
 */
public interface AdminAgentActionItemService {

    /**
     * 分页查询 Agent 行动项。
     *
     * @param query 查询条件
     * @return 行动项分页结果
     */
    IPage<AgentActionItemVO> pageActions(AgentActionItemQueryDTO query);
}
