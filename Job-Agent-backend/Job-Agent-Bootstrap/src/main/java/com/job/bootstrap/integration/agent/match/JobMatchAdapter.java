package com.job.bootstrap.integration.agent.match;

import com.job.ai.agent.port.JobMatchPort;
import com.job.bootstrap.service.JobMatchService;
import com.job.common.vo.match.JobMatchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 功能: Agent 岗位匹配端口的宿主实现，委托现有 JobMatchService。 */
@Component
@RequiredArgsConstructor
public class JobMatchAdapter implements JobMatchPort {

    private final JobMatchService jobMatchService;

    @Override
    public JobMatchVO matchJob(Long userId, Long resumeId, Long jobId) {
        return jobMatchService.matchJob(userId, resumeId, jobId);
    }

    @Override
    public JobMatchVO matchJob(Long userId, Long resumeId, Long jobId, Map<String, Object> explicitParams) {
        return jobMatchService.matchJob(userId, resumeId, jobId, explicitParams);
    }

    @Override
    public JobMatchVO previewMatch(Long userId, Long resumeId, Long jobId, Map<String, Object> explicitParams) {
        return jobMatchService.previewMatch(userId, resumeId, jobId, explicitParams);
    }
}
