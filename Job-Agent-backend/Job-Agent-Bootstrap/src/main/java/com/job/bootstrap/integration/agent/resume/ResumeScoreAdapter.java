package com.job.bootstrap.integration.agent.resume;

import com.job.ai.agent.port.ResumeScorePort;
import com.job.bootstrap.service.JobResumeScoreService;
import com.job.common.vo.resume.ResumeScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 功能: Agent 简历评分端口的宿主实现（Bootstrap 侧）。
 *
 * <p>直接委托 {@link JobResumeScoreService}，评分规则、缓存与数据权限保持原实现。</p>
 */
@Component
@RequiredArgsConstructor
public class ResumeScoreAdapter implements ResumeScorePort {

    private final JobResumeScoreService jobResumeScoreService;

    @Override
    public ResumeScoreVO scoreResume(Long userId, Long resumeId, String targetPosition) {
        return jobResumeScoreService.scoreResume(userId, resumeId, targetPosition);
    }
}
