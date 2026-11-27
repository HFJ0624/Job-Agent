package com.job.agent.port;

import com.job.common.dto.preference.JobRecommendQueryDTO;
import com.job.common.vo.preference.JobRecommendVO;

import java.util.List;

/**
 * 功能: Agent 岗位推荐端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>凭用户长期偏好召回岗位；偏好读取与召回排序保持原有实现。</p>
 */
public interface JobRecommendPort {

    List<JobRecommendVO> recommendJobs(Long userId, JobRecommendQueryDTO query);
}
