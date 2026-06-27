package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.bootstrap.service.WorkflowTaskProgressService;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.dto.workflow.WorkflowTaskQueryDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.workflow.WorkflowTaskVO;
import com.job.common.vo.workflow.WorkflowTaskLogVO;
import com.job.enums.WorkflowTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台工作流任务队列接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/workflow/tasks")
public class AdminWorkflowTaskController {

    private final WorkflowTaskService workflowTaskService;
    private final WorkflowTaskProgressService workflowTaskProgressService;

    /**
     * 创建通用工作流任务。
     */
    @PostMapping
    public Result<WorkflowTaskVO> create(@RequestBody WorkflowTaskCreateDTO request) {
        return Result.build(workflowTaskService.createTask(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 创建 RAG 全量重建任务。
     */
    @PostMapping("/rag/rebuild-all")
    public Result<WorkflowTaskVO> createRagRebuildAll() {
        WorkflowTaskCreateDTO request = new WorkflowTaskCreateDTO();
        request.setTaskType(WorkflowTaskType.RAG_REBUILD_ALL.name());
        return Result.build(workflowTaskService.createTask(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 创建指定用户 RAG 重建任务。
     */
    @PostMapping("/rag/rebuild-users/{userId}")
    public Result<WorkflowTaskVO> createRagRebuildUser(@PathVariable Long userId) {
        WorkflowTaskCreateDTO request = new WorkflowTaskCreateDTO();
        request.setTaskType(WorkflowTaskType.RAG_REBUILD_USER.name());
        request.setUserId(userId);
        request.setBizId(userId);
        return Result.build(workflowTaskService.createTask(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 创建 Eval 数据集批量回归任务。
     */
    @PostMapping("/eval/datasets/{datasetId}/run")
    public Result<WorkflowTaskVO> createEvalRunDataset(@PathVariable Long datasetId) {
        WorkflowTaskCreateDTO request = new WorkflowTaskCreateDTO();
        request.setTaskType(WorkflowTaskType.AGENT_EVAL_RUN_DATASET.name());
        request.setBizId(datasetId);
        return Result.build(workflowTaskService.createTask(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 分页查询任务。
     */
    @GetMapping("/page")
    public Result<IPage<WorkflowTaskVO>> page(WorkflowTaskQueryDTO query) {
        return Result.build(workflowTaskService.pageTasks(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询任务详情。
     */
    @GetMapping("/{id}")
    public Result<WorkflowTaskVO> detail(@PathVariable Long id) {
        return Result.build(workflowTaskService.getDetail(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询任务阶段日志。
     */
    @GetMapping("/{id}/logs")
    public Result<List<WorkflowTaskLogVO>> logs(@PathVariable Long id) {
        return Result.build(workflowTaskProgressService.listLogs(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 手动重试任务。
     */
    @PostMapping("/{id}/retry")
    public Result<WorkflowTaskVO> retry(@PathVariable Long id) {
        return Result.build(workflowTaskService.retryTask(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 手动取消任务。
     */
    @PostMapping("/{id}/cancel")
    public Result<WorkflowTaskVO> cancel(@PathVariable Long id) {
        return Result.build(workflowTaskService.cancelTask(id), ResultCodeEnum.SUCCESS);
    }
}
