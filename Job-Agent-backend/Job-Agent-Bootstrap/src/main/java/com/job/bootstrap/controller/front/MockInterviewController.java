package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.MockInterviewService;
import com.job.common.dto.interview.MockInterviewAnswerDTO;
import com.job.common.dto.interview.MockInterviewStartDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.interview.MockInterviewAnswerVO;
import com.job.common.vo.interview.MockInterviewQuestionVO;
import com.job.common.vo.interview.MockInterviewSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 作者:hfj
 * 功能:前台模拟面试接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/mock-interviews")
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;

    /**
     * 开始一轮模拟面试。
     */
    @PostMapping("/start")
    public Result<MockInterviewSessionVO> start(
            @Valid @RequestBody MockInterviewStartDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        MockInterviewSessionVO vo = mockInterviewService.startSession(userId, dto);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询模拟面试详情。
     */
    @GetMapping("/{sessionId}")
    public Result<MockInterviewSessionVO> detail(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();

        MockInterviewSessionVO vo = mockInterviewService.getSessionDetail(userId, sessionId);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询当前题目。
     */
    @GetMapping("/{sessionId}/current-question")
    public Result<MockInterviewQuestionVO> currentQuestion(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();

        MockInterviewQuestionVO vo = mockInterviewService.getCurrentQuestion(userId, sessionId);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 提交回答。
     */
    @PostMapping("/{sessionId}/answer")
    public Result<MockInterviewAnswerVO> submitAnswer(
            @PathVariable Long sessionId,
            @Valid @RequestBody MockInterviewAnswerDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        MockInterviewAnswerVO vo = mockInterviewService.submitAnswer(userId, sessionId, dto);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 结束模拟面试。
     */
    @PostMapping("/{sessionId}/finish")
    public Result<MockInterviewSessionVO> finish(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();

        MockInterviewSessionVO vo = mockInterviewService.finishSession(userId, sessionId);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }
}
