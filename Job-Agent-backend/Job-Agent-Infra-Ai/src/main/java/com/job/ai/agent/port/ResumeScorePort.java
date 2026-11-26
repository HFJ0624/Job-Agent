package com.job.ai.agent.port;

import com.job.common.vo.resume.ResumeScoreVO;

/**
 * 功能: Agent 简历评分端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>只暴露简历分析 Agent 需要的能力：按用户、简历、目标岗位打分。
 * 评分规则、数据权限与落库仍由 Bootstrap 的简历业务负责。</p>
 */
public interface ResumeScorePort {

    /**
     * 对指定简历打分。
     *
     * @param userId         当前用户（服务端身份，模型不可伪造）
     * @param resumeId       简历 ID
     * @param targetPosition 目标岗位方向（可空）
     */
    ResumeScoreVO scoreResume(Long userId, Long resumeId, String targetPosition);
}
