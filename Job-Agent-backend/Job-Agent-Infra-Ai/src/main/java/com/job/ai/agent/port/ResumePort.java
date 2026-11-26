package com.job.ai.agent.port;

import com.job.common.entity.resume.JobResume;

import java.util.List;

/**
 * 功能: Agent 简历数据端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>只暴露场景真正需要的能力：按 ID 取（带归属校验）、列出用户简历、按需解析。
 * 简历上传、解析实现与数据权限仍由 Bootstrap 简历业务负责。</p>
 */
public interface ResumePort {

    /** 取指定简历；不存在或不属于该用户时抛业务异常。 */
    JobResume getUserResumeRequired(Long userId, Long resumeId);

    /** 列出用户的全部简历（用于"多份简历需要选择"场景）。 */
    List<JobResume> listUserResumes(Long userId);

    /** 确保简历已解析；未解析时触发解析并返回最新数据。 */
    JobResume parseResumeText(Long userId, Long resumeId);
}
