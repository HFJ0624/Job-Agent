package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentTraceLogQueryDTO;
import com.job.common.vo.agent.AgentTraceLogVO;

/**
 * 作者:hfj
 * 功能:后台 Agent Trace 日志服务
 */
public interface AdminAgentTraceLogService {

    /**
     * 分页查询 Agent Trace 日志。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<AgentTraceLogVO> pageLogs(AgentTraceLogQueryDTO query);

    /**
     * 查询 Agent Trace 日志详情。
     *
     * @param id 日志ID
     * @return 日志详情
     */
    AgentTraceLogVO getDetail(Long id);
}
