package com.job.ai.agent.port;

import com.job.common.vo.greeting.GreetingVO;

/** 功能: Agent 打招呼话术端口（Infra-Ai 定义，Bootstrap 实现）。 */
public interface GreetingPort {

    GreetingVO generateGreeting(Long userId, Long resumeId, Long jobId, String style);
}
