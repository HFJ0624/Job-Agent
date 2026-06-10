package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobReminderService;
import com.job.common.dto.reminder.ReminderCreateDTO;
import com.job.common.dto.reminder.ReminderPostponeDTO;
import com.job.common.dto.reminder.ReminderQueryDTO;
import com.job.common.entity.base.Result;
import com.job.common.vo.reminder.JobReminderVO;
import com.job.common.vo.reminder.ReminderPageVO;
import com.job.common.vo.reminder.ReminderStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 作者: hfj
 * 功能: 用户端求职提醒接口
 */
@Tag(name = "用户端-求职提醒接口")
@RestController
@RequestMapping("/front/reminder")
@RequiredArgsConstructor
public class FrontJobReminderController {

    private final JobReminderService jobReminderService;

    /**
     * 分页查询提醒。
     */
    @Operation(summary = "分页查询求职提醒")
    @GetMapping("/page")
    public Result<ReminderPageVO> page(ReminderQueryDTO queryDTO) {
        Long userId = StpUtil.getLoginIdAsLong();

        ReminderPageVO pageVO = jobReminderService.pageReminders(userId, queryDTO);

        return Result.build(pageVO, 200, "查询成功");
    }

    /**
     * 查询提醒统计。
     */
    @Operation(summary = "查询求职提醒统计")
    @GetMapping("/stats")
    public Result<ReminderStatsVO> stats() {
        Long userId = StpUtil.getLoginIdAsLong();

        ReminderStatsVO statsVO = jobReminderService.getStats(userId);

        return Result.build(statsVO, 200, "查询成功");
    }

    /**
     * 查询已到期提醒。
     */
    @Operation(summary = "查询已到期提醒")
    @GetMapping("/due")
    public Result<List<JobReminderVO>> due() {
        Long userId = StpUtil.getLoginIdAsLong();

        List<JobReminderVO> list = jobReminderService.listDueReminders(userId);

        return Result.build(list, 200, "查询成功");
    }

    /**
     * 创建自定义提醒。
     */
    @Operation(summary = "创建自定义提醒")
    @PostMapping
    public Result<JobReminderVO> create(@RequestBody ReminderCreateDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobReminderVO vo = jobReminderService.createReminder(userId, dto);

        return Result.build(vo, 200, "创建成功");
    }

    /**
     * 标记提醒完成。
     */
    @Operation(summary = "标记提醒完成")
    @PostMapping("/{id}/done")
    public Result<JobReminderVO> done(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobReminderVO vo = jobReminderService.markDone(userId, id);

        return Result.build(vo, 200, "已完成");
    }

    /**
     * 标记已读。
     */
    @Operation(summary = "标记提醒已读")
    @PostMapping("/{id}/read")
    public Result<JobReminderVO> read(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobReminderVO vo = jobReminderService.markRead(userId, id);

        return Result.build(vo, 200, "已读");
    }

    /**
     * 取消提醒。
     */
    @Operation(summary = "取消提醒")
    @PostMapping("/{id}/cancel")
    public Result<JobReminderVO> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobReminderVO vo = jobReminderService.cancelReminder(userId, id, reason);

        return Result.build(vo, 200, "已取消");
    }

    /**
     * 延期提醒。
     */
    @Operation(summary = "延期提醒")
    @PostMapping("/{id}/postpone")
    public Result<JobReminderVO> postpone(
            @PathVariable Long id,
            @RequestBody ReminderPostponeDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobReminderVO vo = jobReminderService.postponeReminder(userId, id, dto);

        return Result.build(vo, 200, "延期成功");
    }
}
