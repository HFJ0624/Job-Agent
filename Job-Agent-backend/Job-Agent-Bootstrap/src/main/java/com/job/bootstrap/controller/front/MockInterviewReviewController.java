package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.MockInterviewReviewService;
import com.job.common.dto.interview.MockInterviewReviewGenerateDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.common.vo.interview.MockInterviewStudyPlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 作者:hfj
 * 功能:前台模拟面试复盘报告接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/mock-interview-reviews")
public class MockInterviewReviewController {

    private final MockInterviewReviewService mockInterviewReviewService;

    /**
     * 生成复盘报告。
     */
    @PostMapping("/generate")
    public Result<MockInterviewReviewVO> generate(
            @Valid @RequestBody MockInterviewReviewGenerateDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        MockInterviewReviewVO vo = mockInterviewReviewService.generateReview(
                userId,
                dto.getSessionId()
        );

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询最近一次复盘报告。
     */
    @GetMapping("/latest")
    public Result<MockInterviewReviewVO> latest(@RequestParam Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();

        MockInterviewReviewVO vo = mockInterviewReviewService.getLatestReview(userId, sessionId);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 根据最近一次复盘报告生成补课清单。
     */
    @GetMapping("/study-plan")
    public Result<MockInterviewStudyPlanVO> studyPlan(@RequestParam Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        MockInterviewStudyPlanVO vo = mockInterviewReviewService.buildStudyPlan(userId, sessionId);
        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }
}
