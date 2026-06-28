package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobApplyDecisionService;
import com.job.bootstrap.service.JobGreetingService;
import com.job.bootstrap.service.JobMatchService;
import com.job.common.dto.decision.JobApplyDecisionRequestDTO;
import com.job.common.dto.greeting.GreetingGenerateRequestDTO;
import com.job.common.dto.match.JobMatchRequestDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.decision.JobApplyDecisionVO;
import com.job.common.vo.greeting.GreetingVO;
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

    private final JobGreetingService jobGreetingService;

    private final JobApplyDecisionService jobApplyDecisionService;

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

    /**
     * 根据指定简历和岗位生成 HR 打招呼语。
     *
     * @param jobId 岗位ID
     * @param request 请求参数，包含 resumeId 和 style
     * @return 生成后的打招呼语
     */
    @PostMapping("/{jobId}/greeting")
    public Result<GreetingVO> generateGreeting(
            @PathVariable Long jobId,
            @Valid @RequestBody GreetingGenerateRequestDTO request
    ) {
        // 1. 当前用户ID必须从登录态获取，不能由前端传入。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 调用服务生成打招呼语。
        GreetingVO result = jobGreetingService.generateGreeting(
                userId,
                request.getResumeId(),
                jobId,
                request.getStyle()
        );

        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * 生成 AI 投递决策。
     */
    @PostMapping("/{jobId}/apply-decision")
    public Result<JobApplyDecisionVO> generateApplyDecision(
            @PathVariable Long jobId,
            @Valid @RequestBody JobApplyDecisionRequestDTO request
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        JobApplyDecisionVO result = jobApplyDecisionService.generateDecision(userId, request.getResumeId(), jobId);
        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询最近一次 AI 投递决策。
     */
    @GetMapping("/{jobId}/apply-decision")
    public Result<JobApplyDecisionVO> latestApplyDecision(
            @PathVariable Long jobId,
            @RequestParam Long resumeId
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        JobApplyDecisionVO result = jobApplyDecisionService.getLatestDecision(userId, resumeId, jobId);
        return Result.build(result, ResultCodeEnum.SUCCESS);
    }
}
