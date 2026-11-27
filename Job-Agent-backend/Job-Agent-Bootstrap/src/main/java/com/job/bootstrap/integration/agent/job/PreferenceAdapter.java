package com.job.bootstrap.integration.agent.job;

import com.job.agent.port.PreferencePort;
import com.job.bootstrap.application.job.service.UserJobPreferenceService;
import com.job.common.vo.preference.UserJobPreferenceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 功能: Agent 偏好端口的宿主实现，委托现有 UserJobPreferenceService。 */
@Component
@RequiredArgsConstructor
public class PreferenceAdapter implements PreferencePort {

    private final UserJobPreferenceService userJobPreferenceService;

    @Override
    public UserJobPreferenceVO getPreference(Long userId) {
        return userJobPreferenceService.getPreference(userId);
    }
}
