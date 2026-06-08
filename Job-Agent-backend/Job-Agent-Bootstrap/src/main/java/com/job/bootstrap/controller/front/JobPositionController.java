package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobMatchService;
import com.job.common.dto.match.JobMatchRequestDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.match.JobMatchVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * 作者:hfj
 * 功能:前台岗位接口
 * 日期: 2026/6/8 11:04
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/job")
public class JobPositionController {

    private final JobMatchService jobMatchService;

    /**
     * 分析当前用户某份简历与指定岗位的匹配度。
     *
     * @param jobId 岗位ID
     * @param request 请求参数，包含 resumeId
     * @return 匹配分析结果
     */
    @PostMapping("/{jobId}/match")
    public Result<JobMatchVO> matchJob(
            @PathVariable Long jobId,
            @Valid @RequestBody JobMatchRequestDTO request
    ) {
        // 1. 用户ID必须从登录态获取，不能相信前端传来的 userId。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 执行岗位匹配分析。
        JobMatchVO result = jobMatchService.matchJob(userId, request.getResumeId(), jobId);

        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询最近一次岗位匹配结果。
     *
     * @param jobId 岗位ID
     * @param resumeId 简历ID
     * @return 最近一次匹配结果
     */
    @GetMapping("/{jobId}/match-record")
    public Result<JobMatchVO> latestMatch(
            @PathVariable Long jobId,
            @RequestParam Long resumeId
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobMatchVO result = jobMatchService.getLatestMatch(userId, resumeId, jobId);

        return Result.build(result, ResultCodeEnum.SUCCESS);
    }
}
