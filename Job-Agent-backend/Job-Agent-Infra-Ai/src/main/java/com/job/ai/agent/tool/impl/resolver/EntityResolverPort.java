package com.job.ai.agent.tool.impl.resolver;

import com.job.common.entity.resume.JobResume;

/**
 * 功能: Agent 工具实体解析端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>按"用户 + 简历名/ID""岗位名/ID"解析出可信实体；查询条件、默认简历规则与
 * 发布状态过滤属于求职业务，由 Bootstrap 适配器实现。</p>
 */
public interface EntityResolverPort {

    JobResume resolveResumeRequired(Long userId, Long resumeId, String resumeName, String toolName);

    AgentEntityResolveResult resolveJob(Long jobId, String jobTitle, String toolName);
}
