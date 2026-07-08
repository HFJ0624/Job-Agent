package com.job.bootstrap.service;

import com.job.common.dto.preference.JobRecommendQueryDTO;
import com.job.common.dto.preference.UserJobPreferenceSaveDTO;
import com.job.common.vo.preference.JobRecommendVO;
import com.job.common.vo.preference.UserJobPreferenceVO;

import java.util.List;

/**
 * 用户求职偏好服务接口。
 *
 * <p>核心职责：管理用户的求职意向、期望薪资、目标城市等偏好配置，并基于偏好提供岗位推荐。</p>
 *
 * <p>所属业务模块：用户中心 - 求职偏好</p>
 *
 * <p>主要调用链：
 * UserJobPreferenceController / FrontHomeService -&gt; UserJobPreferenceService -&gt; UserJobPreferenceServiceImpl -&gt; UserJobPreferenceRepository / JobPositionRepository</p>
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
