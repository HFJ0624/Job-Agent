package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentTraceLogQueryDTO;
import com.job.common.vo.agent.AgentTraceLogVO;

/**
 * 后台 Agent Trace 日志服务接口。
 *
 * <p>核心职责：为研发和运维人员提供 Agent Trace 日志的分页查询和详情查看，支持链路追踪和问题定位。</p>
 *
 * <p>所属业务模块：后台管理 - Agent 运维</p>
 *
 * <p>主要调用链：
 * AdminAgentTraceLogController -&gt; AdminAgentTraceLogService -&gt; AdminAgentTraceLogServiceImpl -&gt; AgentTraceLogRepository</p>
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
