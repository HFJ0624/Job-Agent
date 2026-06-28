package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.MockInterviewWrongQuestionService;
import com.job.common.dto.interview.MockInterviewWrongQuestionQueryDTO;
import com.job.common.dto.interview.MockInterviewWrongQuestionStatusDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.interview.MockInterviewWrongQuestionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台模拟面试错题本接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/mock-interview-wrong-questions")
public class MockInterviewWrongQuestionController {

    private final MockInterviewWrongQuestionService wrongQuestionService;

    /**
     * 分页查询当前用户错题本。
     */
    @GetMapping("/page")
    public Result<IPage<MockInterviewWrongQuestionVO>> pageWrongQuestions(MockInterviewWrongQuestionQueryDTO query) {
        Long userId = StpUtil.getLoginIdAsLong();
        IPage<MockInterviewWrongQuestionVO> page = wrongQuestionService.pageWrongQuestions(userId, query);
        return Result.build(page, ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改错题掌握状态。
     */
    @PutMapping("/{id}/mastery-status")
    public Result<MockInterviewWrongQuestionVO> updateMasteryStatus(
            @PathVariable Long id,
            @Valid @RequestBody MockInterviewWrongQuestionStatusDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        MockInterviewWrongQuestionVO vo = wrongQuestionService.updateMasteryStatus(userId, id, dto);
        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }
}
