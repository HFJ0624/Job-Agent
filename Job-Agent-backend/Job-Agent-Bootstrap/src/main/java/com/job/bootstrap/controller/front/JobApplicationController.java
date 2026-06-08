package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.JobApplicationService;
import com.job.common.dto.application.JobApplicationQueryDTO;
import com.job.common.dto.application.JobApplicationSaveDTO;
import com.job.common.dto.application.JobApplicationStatusUpdateDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.application.JobApplicationStatsVO;
import com.job.common.vo.application.JobApplicationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 作者:hfj
 * 功能:前台求职进度管理接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/applications")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    /**
     * 新增或更新求职记录。
     */
    @PostMapping
    public Result<JobApplicationVO> saveApplication(
            @Valid @RequestBody JobApplicationSaveDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobApplicationVO vo = jobApplicationService.saveApplication(userId, dto);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 分页查询求职记录。
     */
    @GetMapping("/page")
    public Result<IPage<JobApplicationVO>> pageApplications(JobApplicationQueryDTO query) {
        Long userId = StpUtil.getLoginIdAsLong();

        IPage<JobApplicationVO> page = jobApplicationService.pageApplications(userId, query);

        return Result.build(page, ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改求职状态。
     */
    @PutMapping("/{id}/status")
    public Result<JobApplicationVO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody JobApplicationStatusUpdateDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        JobApplicationVO vo = jobApplicationService.updateStatus(userId, id, dto);

        return Result.build(vo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除求职记录。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteApplication(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        jobApplicationService.deleteApplication(userId, id);

        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询求职进度统计。
     */
    @GetMapping("/stats")
    public Result<JobApplicationStatsVO> getStats() {
        Long userId = StpUtil.getLoginIdAsLong();

        JobApplicationStatsVO stats = jobApplicationService.getStats(userId);

        return Result.build(stats, ResultCodeEnum.SUCCESS);
    }
}
