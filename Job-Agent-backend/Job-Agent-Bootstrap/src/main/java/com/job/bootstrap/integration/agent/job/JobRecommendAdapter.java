package com.job.bootstrap.integration.agent.job;

import com.job.common.dto.preference.JobRecommendQueryDTO;
import com.job.agent.port.JobRecommendPort;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.vo.preference.JobRecommendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 功能: Agent 岗位推荐端口的宿主实现，委托现有 UserJobPreferenceService。 */
@Component
@RequiredArgsConstructor
public class JobRecommendAdapter implements JobRecommendPort {

    private final UserJobPreferenceService userJobPreferenceService;

    @Override
    public List<JobRecommendVO> recommendJobs(Long userId, JobRecommendQueryDTO query) {
        return userJobPreferenceService.recommendJobs(userId, query);
    }
}
