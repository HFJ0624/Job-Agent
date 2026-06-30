package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.HrReplyRecognitionService;
import com.job.common.dto.communication.HrReplyRecognitionConfirmDTO;
import com.job.common.dto.communication.HrReplyRecognizeDTO;
import com.job.common.entity.base.Result;
import com.job.common.vo.communication.HrReplyRecognitionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 HR 回复识别接口。
 *
 * 使用场景：
 * 1. 沟通记录页：用户粘贴 HR 回复，AI 识别意图并等待用户确认。
 * 2. 跟进中心：用户在岗位卡片上粘贴 HR 回复，AI 识别后更新求职进度。
 */
@Tag(name = "用户端 HR 回复识别接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/hr-reply-recognitions")
public class FrontHrReplyRecognitionController {

    private final HrReplyRecognitionService hrReplyRecognitionService;

    @Operation(summary = "从沟通记录识别 HR 回复")
    @PostMapping("/communications/{communicationId}/recognize")
    public Result<HrReplyRecognitionVO> recognizeFromCommunication(
            @PathVariable Long communicationId,
            @RequestBody HrReplyRecognizeDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(
                hrReplyRecognitionService.recognizeFromCommunication(userId, communicationId, dto),
                200,
                "识别成功"
        );
    }

    @Operation(summary = "从求职记录识别 HR 回复")
    @PostMapping("/applications/{applicationId}/recognize")
    public Result<HrReplyRecognitionVO> recognizeFromApplication(
            @PathVariable Long applicationId,
            @RequestBody HrReplyRecognizeDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(
                hrReplyRecognitionService.recognizeFromApplication(userId, applicationId, dto),
                200,
                "识别成功"
        );
    }

    @Operation(summary = "确认并执行 HR 回复识别动作")
    @PostMapping("/{id}/confirm")
    public Result<HrReplyRecognitionVO> confirm(
            @PathVariable Long id,
            @RequestBody HrReplyRecognitionConfirmDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(hrReplyRecognitionService.confirm(userId, id, dto), 200, "确认成功");
    }

    @Operation(summary = "取消 HR 回复识别结果")
    @PostMapping("/{id}/cancel")
    public Result<HrReplyRecognitionVO> cancel(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(hrReplyRecognitionService.cancel(userId, id), 200, "已取消");
    }
}
