package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.MockInterviewLearningPlanService;
import com.job.common.dto.interview.MockInterviewStudyPlanGenerateDTO;
import com.job.common.dto.interview.MockInterviewStudyPlanItemStatusDTO;
import com.job.common.dto.interview.MockInterviewStudyPlanRetestSubmitDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.interview.MockInterviewLearningPlanVO;
import com.job.common.vo.interview.MockInterviewStudyPlanRetestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台 AI 面试学习计划接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/mock-interview-learning-plans")
public class MockInterviewLearningPlanController {

    private final MockInterviewLearningPlanService learningPlanService;

    /**
     * 基于错题本生成学习计划。
     */
    @PostMapping("/generate")
    public Result<MockInterviewLearningPlanVO> generatePlan(@RequestBody MockInterviewStudyPlanGenerateDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        MockInterviewLearningPlanVO vo = learningPlanService.generatePlan(userId, dto);
        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询最新学习计划。
     */
    @GetMapping("/latest")
    public Result<MockInterviewLearningPlanVO> latestPlan() {
        Long userId = StpUtil.getLoginIdAsLong();
        MockInterviewLearningPlanVO vo = learningPlanService.getLatestPlan(userId);
        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改每日任务完成状态。
     */
    @PutMapping("/items/{itemId}/completion-status")
    public Result<MockInterviewLearningPlanVO> updateItemStatus(
            @PathVariable Long itemId,
            @Valid @RequestBody MockInterviewStudyPlanItemStatusDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        MockInterviewLearningPlanVO vo = learningPlanService.updateItemStatus(userId, itemId, dto);
        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 为某个学习任务发起复测。
     */
    @PostMapping("/items/{itemId}/retests/start")
    public Result<MockInterviewStudyPlanRetestVO> startRetest(@PathVariable Long itemId) {
        Long userId = StpUtil.getLoginIdAsLong();
        MockInterviewStudyPlanRetestVO vo = learningPlanService.startRetest(userId, itemId);
        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 提交复测答案。
     */
    @PostMapping("/retests/{retestId}/submit")
    public Result<MockInterviewStudyPlanRetestVO> submitRetest(
            @PathVariable Long retestId,
            @Valid @RequestBody MockInterviewStudyPlanRetestSubmitDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        MockInterviewStudyPlanRetestVO vo = learningPlanService.submitRetest(userId, retestId, dto);
        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }
}
