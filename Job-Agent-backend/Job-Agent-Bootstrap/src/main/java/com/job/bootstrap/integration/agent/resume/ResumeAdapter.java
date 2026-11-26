package com.job.bootstrap.integration.agent.resume;

import com.job.ai.agent.port.ResumePort;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.resume.JobResume;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 功能: Agent 简历端口的宿主实现，委托现有 JobResumeService。 */
@Component
@RequiredArgsConstructor
public class ResumeAdapter implements ResumePort {

    private final JobResumeService jobResumeService;

    @Override
    public JobResume getUserResumeRequired(Long userId, Long resumeId) {
        return jobResumeService.getUserResumeRequired(userId, resumeId);
    }

    @Override
    public List<JobResume> listUserResumes(Long userId) {
        return jobResumeService.listUserResumes(userId);
    }

    @Override
    public JobResume parseResumeText(Long userId, Long resumeId) {
        return jobResumeService.parseResumeText(userId, resumeId);
    }
}
