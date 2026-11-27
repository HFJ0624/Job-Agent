package com.job.agent.port;

import com.job.common.vo.preference.UserJobPreferenceVO;

/**
 * 功能: Agent 求职偏好端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>只读：搜索入口需要把长期偏好合并进本轮条件。</p>
 */
public interface PreferencePort {

    UserJobPreferenceVO getPreference(Long userId);
}
