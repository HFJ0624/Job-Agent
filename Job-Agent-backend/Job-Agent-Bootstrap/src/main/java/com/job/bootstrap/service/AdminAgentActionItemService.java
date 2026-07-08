package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentActionItemQueryDTO;
import com.job.common.vo.agent.AgentActionItemVO;

/**
 * Admin Agent 行动项管理服务。
 *
 * <p>核心职责：为管理员提供 Agent 生成的用户行动项查询能力，支持运营人员查看系统建议用户执行的任务列表。</p>
 *
 * <p>所属业务模块：Agent 行动中心 / 后台运营</p>
 *
 * <p>主要调用链：Admin Controller → AdminAgentActionItemService → AgentActionItem 领域 Service / Mapper</p>
 */
public interface AdminAgentActionItemService {

    /**
     * 分页查询 Agent 行动项列表。
     *
     * @param query 查询条件（包含用户 ID、行动类型、状态、创建时间范围等过滤条件）
     * @return Agent 行动项分页结果
     */
    IPage<AgentActionItemVO> pageActions(AgentActionItemQueryDTO query);
}
