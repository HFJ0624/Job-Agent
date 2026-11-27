package com.job.agent.port;

import com.job.common.vo.match.JobMatchVO;

import java.util.Map;

/**
 * 功能: Agent 岗位匹配端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>两段式：无显式条件走长期偏好，有显式条件参与硬性判定。
 * 匹配算法、数据权限与落库仍由 Bootstrap 的匹配业务负责。</p>
 */
public interface JobMatchPort {

    JobMatchVO matchJob(Long userId, Long resumeId, Long jobId);

    JobMatchVO matchJob(Long userId, Long resumeId, Long jobId, Map<String, Object> explicitParams);

    /** 预览匹配（不落库、不耗额度），用于告警/建议场景。 */
    JobMatchVO previewMatch(Long userId, Long resumeId, Long jobId, Map<String, Object> explicitParams);
}
