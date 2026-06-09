package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.common.dto.interview.InterviewPrepareGenerateDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.interview.InterviewPrepareVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 作者:hfj
 * 功能:前台 AI 面试准备接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/interview-prepare")
public class InterviewPrepareController {

    private final InterviewPrepareService interviewPrepareService;

    /**
     * 生成面试准备。
     */
    @PostMapping("/generate")
    public Result<InterviewPrepareVO> generate(
            @Valid @RequestBody InterviewPrepareGenerateDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        InterviewPrepareVO vo = interviewPrepareService.generatePrepare(
                userId,
                dto.getApplicationId(),
                dto.getResumeId()
        );

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询某条求职记录最近一次面试准备。
     */
    @GetMapping("/latest")
    public Result<InterviewPrepareVO> latest(@RequestParam Long applicationId) {
        Long userId = StpUtil.getLoginIdAsLong();

        InterviewPrepareVO vo = interviewPrepareService.getLatestPrepare(userId, applicationId);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }
}
