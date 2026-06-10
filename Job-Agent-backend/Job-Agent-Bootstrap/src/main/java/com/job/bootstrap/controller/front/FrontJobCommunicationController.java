package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobCommunicationRecordService;
import com.job.common.dto.communication.*;
import com.job.common.entity.base.Result;
import com.job.common.vo.communication.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 作者: hfj
 * 功能: 用户端求职沟通记录接口
 *
 * 说明:
 * 1. 这个 Controller 面向用户端前端。
 * 2. 用户可以查看沟通记录、录入 HR 回复、标记面试邀约。
 * 3. 不做真实平台自动发送，只记录用户在 Boss 直聘等外部平台的沟通进度。
 */
@Tag(name = "用户端-求职沟通记录接口")
@RestController
@RequestMapping("/front/communication")
@RequiredArgsConstructor
public class FrontJobCommunicationController {

    private final JobCommunicationRecordService jobCommunicationRecordService;

    /**
     * 分页查询沟通记录。
     */
    @Operation(summary = "分页查询求职沟通记录")
    @GetMapping("/page")
    public Result<JobCommunicationPageVO> pageCommunications(JobCommunicationQueryDTO queryDTO) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationPageVO pageVO = jobCommunicationRecordService.pageCommunications(userId, queryDTO);

        return Result.build(pageVO, 200, "查询成功");
    }

    /**
     * 查询统计信息。
     */
    @Operation(summary = "查询求职沟通统计")
    @GetMapping("/stats")
    public Result<JobCommunicationStatsVO> stats() {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationStatsVO statsVO = jobCommunicationRecordService.getStats(userId);

        return Result.build(statsVO, 200, "查询成功");
    }

    /**
     * 查询详情。
     */
    @Operation(summary = "查询求职沟通详情")
    @GetMapping("/{id}")
    public Result<JobCommunicationRecordVO> detail(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.getDetail(userId, id);

        return Result.build(vo, 200, "查询成功");
    }

    /**
     * 手动新增沟通记录。
     */
    @Operation(summary = "新增求职沟通记录")
    @PostMapping
    public Result<JobCommunicationRecordVO> create(@RequestBody JobCommunicationCreateDTO createDTO) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.create(userId, createDTO);

        return Result.build(vo, 200, "创建成功");
    }

    /**
     * 标记话术已复制。
     */
    @Operation(summary = "标记已复制话术")
    @PostMapping("/{id}/copied")
    public Result<JobCommunicationRecordVO> markCopied(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.markCopied(userId, id);

        return Result.build(vo, 200, "已标记复制");
    }

    /**
     * 标记已沟通。
     */
    @Operation(summary = "标记已沟通")
    @PostMapping("/{id}/communicated")
    public Result<JobCommunicationRecordVO> markCommunicated(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.markCommunicated(userId, id);

        return Result.build(vo, 200, "已标记沟通");
    }

    /**
     * 保存 HR 回复。
     */
    @Operation(summary = "保存HR回复")
    @PostMapping("/{id}/reply")
    public Result<JobCommunicationRecordVO> saveReply(
            @PathVariable Long id,
            @RequestBody JobCommunicationReplyDTO replyDTO
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.saveHrReply(userId, id, replyDTO);

        return Result.build(vo, 200, "保存成功");
    }

    /**
     * 标记面试邀约。
     */
    @Operation(summary = "标记面试邀约")
    @PostMapping("/{id}/interview")
    public Result<JobCommunicationRecordVO> markInterview(
            @PathVariable Long id,
            @RequestBody JobCommunicationInterviewDTO interviewDTO
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.markInterviewInvited(userId, id, interviewDTO);

        return Result.build(vo, 200, "已标记面试邀约");
    }

    /**
     * 关闭沟通记录。
     */
    @Operation(summary = "关闭沟通记录")
    @PostMapping("/{id}/close")
    public Result<JobCommunicationRecordVO> close(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.closeCommunication(userId, id);

        return Result.build(vo, 200, "已关闭");
    }

    /**
     * 保存 HR 回复并生成 AI 建议回复。
     *
     * 使用场景:
     * 用户从 Boss 直聘复制 HR 回复后，粘贴到系统里。
     * 系统保存 HR 回复，并生成一段可复制给 HR 的回复建议。
     */
    @Operation(summary = "保存HR回复并生成AI建议回复")
    @PostMapping("/{id}/hr-reply/generate")
    public Result<JobCommunicationRecordVO> saveHrReplyAndGenerateReply(
            @PathVariable Long id,
            @RequestBody HrReplyGenerateDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.saveHrReplyAndGenerateReply(
                userId,
                id,
                dto
        );

        return Result.build(vo, 200, "AI回复已生成");
    }

    /**
     * 标记用户已发送回复给 HR。
     */
    @Operation(summary = "标记已发送回复给HR")
    @PostMapping("/{id}/user-reply/sent")
    public Result<JobCommunicationRecordVO> markUserReplySent(
            @PathVariable Long id,
            @RequestBody UserReplySentDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.markUserReplySent(
                userId,
                id,
                dto
        );

        return Result.build(vo, 200, "已标记发送给HR");
    }

    /**
     * 手动更新沟通状态。
     */
    @Operation(summary = "手动更新沟通状态")
    @PostMapping("/{id}/status")
    public Result<JobCommunicationRecordVO> updateStatus(
            @PathVariable Long id,
            @RequestBody CommunicationStatusUpdateDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.updateStatus(
                userId,
                id,
                dto
        );

        return Result.build(vo, 200, "状态更新成功");
    }

    /**
     * 查询沟通消息流水。
     */
    @Operation(summary = "查询沟通消息流水")
    @GetMapping("/{id}/messages")
    public Result<List<JobCommunicationMessageVO>> listMessages(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        List<JobCommunicationMessageVO> list = jobCommunicationRecordService.listMessages(userId, id);

        return Result.build(list, 200, "查询成功");
    }

    /**
     * 从 HR 回复中提取面试邀约信息。
     *
     * 使用场景:
     * 用户复制 HR 回复，例如：
     * “你好，明天下午3点方便线上面试吗？”
     *
     * 系统自动提取：
     * 1. 面试时间
     * 2. 面试方式
     * 3. 面试地点
     * 4. 会议链接
     * 5. 是否需要用户确认
     */
    @Operation(summary = "从HR回复中提取面试邀约信息")
    @PostMapping("/{id}/interview/extract")
    public Result<InterviewInviteExtractVO> extractInterviewInvite(
            @PathVariable Long id,
            @RequestBody InterviewInviteExtractDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        InterviewInviteExtractVO vo = jobCommunicationRecordService.extractInterviewInvite(
                userId,
                id,
                dto
        );

        return Result.build(vo, 200, "提取成功");
    }

    /**
     * 用户确认并保存面试邀约信息。
     *
     * 说明:
     * AI 提取结果可能有偏差，所以最终由用户确认保存。
     */
    @Operation(summary = "确认并保存面试邀约信息")
    @PostMapping("/{id}/interview/confirm")
    public Result<JobCommunicationRecordVO> confirmInterviewInvite(
            @PathVariable Long id,
            @RequestBody InterviewInviteConfirmDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobCommunicationRecordVO vo = jobCommunicationRecordService.confirmInterviewInvite(
                userId,
                id,
                dto
        );

        return Result.build(vo, 200, "面试邀约信息已确认");
    }
}
