package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobCommunicationRecordService;
import com.job.common.dto.communication.JobCommunicationCreateDTO;
import com.job.common.dto.communication.JobCommunicationInterviewDTO;
import com.job.common.dto.communication.JobCommunicationQueryDTO;
import com.job.common.dto.communication.JobCommunicationReplyDTO;
import com.job.common.entity.base.Result;
import com.job.common.vo.communication.JobCommunicationPageVO;
import com.job.common.vo.communication.JobCommunicationRecordVO;
import com.job.common.vo.communication.JobCommunicationStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
}
