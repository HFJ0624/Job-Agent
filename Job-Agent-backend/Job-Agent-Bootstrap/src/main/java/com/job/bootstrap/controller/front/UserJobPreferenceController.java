package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.dto.preference.JobRecommendQueryDTO;
import com.job.common.dto.preference.UserJobPreferenceSaveDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.preference.JobRecommendVO;
import com.job.common.vo.preference.UserJobPreferenceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 作者:hfj
 * 功能:前台用户求职偏好与岗位推荐接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/job-preference")
public class UserJobPreferenceController {

    private final UserJobPreferenceService userJobPreferenceService;

    /**
     * 保存或更新当前用户求职偏好。
     */
    @PostMapping
    public Result<UserJobPreferenceVO> savePreference(
            @Valid @RequestBody UserJobPreferenceSaveDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        UserJobPreferenceVO vo = userJobPreferenceService.saveOrUpdatePreference(userId, dto);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询当前用户求职偏好。
     */
    @GetMapping
    public Result<UserJobPreferenceVO> getPreference() {
        Long userId = StpUtil.getLoginIdAsLong();

        UserJobPreferenceVO vo = userJobPreferenceService.getPreference(userId);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 根据当前用户求职偏好推荐岗位。
     */
    @GetMapping("/recommend")
    public Result<List<JobRecommendVO>> recommendJobs(JobRecommendQueryDTO query) {
        Long userId = StpUtil.getLoginIdAsLong();

        List<JobRecommendVO> list = userJobPreferenceService.recommendJobs(userId, query);

        return Result.build(list, ResultCodeEnum.SUCCESS);
    }
}
