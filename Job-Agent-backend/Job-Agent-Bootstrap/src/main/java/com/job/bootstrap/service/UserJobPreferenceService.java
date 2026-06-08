package com.job.bootstrap.service;

import com.job.common.dto.preference.JobRecommendQueryDTO;
import com.job.common.dto.preference.UserJobPreferenceSaveDTO;
import com.job.common.vo.preference.JobRecommendVO;
import com.job.common.vo.preference.UserJobPreferenceVO;

import java.util.List;

/**
 * 作者:hfj
 * 功能:用户求职偏好服务
 */
public interface UserJobPreferenceService {

    /**
     * 保存或更新当前用户求职偏好。
     *
     * @param userId 当前用户ID
     * @param dto 请求参数
     * @return 求职偏好
     */
    UserJobPreferenceVO saveOrUpdatePreference(Long userId, UserJobPreferenceSaveDTO dto);

    /**
     * 查询当前用户求职偏好。
     *
     * @param userId 当前用户ID
     * @return 求职偏好
     */
    UserJobPreferenceVO getPreference(Long userId);

    /**
     * 根据求职偏好推荐岗位。
     *
     * @param userId 当前用户ID
     * @param query 查询参数
     * @return 推荐岗位列表
     */
    List<JobRecommendVO> recommendJobs(Long userId, JobRecommendQueryDTO query);
}
