package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AdminAiModelService;
import com.job.common.dto.ai.AiModelCallLogQueryDTO;
import com.job.common.dto.ai.AiModelConfigQueryDTO;
import com.job.common.dto.ai.AiModelConfigSaveDTO;
import com.job.common.dto.ai.AiModelRouteQueryDTO;
import com.job.common.dto.ai.AiModelRouteSaveDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.ai.AiModelCallLogVO;
import com.job.common.vo.ai.AiModelConfigVO;
import com.job.common.vo.ai.AiModelCostStatsVO;
import com.job.common.vo.ai.AiModelRouteVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者:hfj
 * 功能:后台 AI 模型、路由和调用日志管理接口
 * 日期:2026/6/21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai/models")
public class AdminAiModelController {

    private final AdminAiModelService adminAiModelService;

    /**
     * 分页查询模型配置。
     *
     * @param query 查询条件
     * @return 模型配置分页
     */
    @GetMapping("/configs/page")
    public Result<IPage<AiModelConfigVO>> pageModels(AiModelConfigQueryDTO query) {
        return Result.build(adminAiModelService.pageModels(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询启用模型列表。
     *
     * @return 启用模型
     */
    @GetMapping("/configs/active")
    public Result<List<AiModelConfigVO>> listActiveModels() {
        return Result.build(adminAiModelService.listActiveModels(), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增模型配置。
     *
     * @param request 模型表单
     * @return 新增后的模型配置
     */
    @PostMapping("/configs")
    public Result<AiModelConfigVO> createModel(@Valid @RequestBody AiModelConfigSaveDTO request) {
        return Result.build(adminAiModelService.createModel(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改模型配置。
     *
     * @param id 模型配置 ID
     * @param request 模型表单
     * @return 修改后的模型配置
     */
    @PutMapping("/configs/{id}")
    public Result<AiModelConfigVO> updateModel(
            @PathVariable Long id,
            @Valid @RequestBody AiModelConfigSaveDTO request
    ) {
        return Result.build(adminAiModelService.updateModel(id, request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除模型配置。
     *
     * @param id 模型配置 ID
     * @return 空结果
     */
    @DeleteMapping("/configs/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        adminAiModelService.deleteModel(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 分页查询模型路由。
     *
     * @param query 查询条件
     * @return 路由分页
     */
    @GetMapping("/routes/page")
    public Result<IPage<AiModelRouteVO>> pageRoutes(AiModelRouteQueryDTO query) {
        return Result.build(adminAiModelService.pageRoutes(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增模型路由。
     *
     * @param request 路由表单
     * @return 新增后的路由
     */
    @PostMapping("/routes")
    public Result<AiModelRouteVO> createRoute(@Valid @RequestBody AiModelRouteSaveDTO request) {
        return Result.build(adminAiModelService.createRoute(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改模型路由。
     *
     * @param id 路由 ID
     * @param request 路由表单
     * @return 修改后的路由
     */
    @PutMapping("/routes/{id}")
    public Result<AiModelRouteVO> updateRoute(
            @PathVariable Long id,
            @Valid @RequestBody AiModelRouteSaveDTO request
    ) {
        return Result.build(adminAiModelService.updateRoute(id, request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除模型路由。
     *
     * @param id 路由 ID
     * @return 空结果
     */
    @DeleteMapping("/routes/{id}")
    public Result<Void> deleteRoute(@PathVariable Long id) {
        adminAiModelService.deleteRoute(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 分页查询模型调用日志。
     *
     * @param query 查询条件
     * @return 调用日志分页
     */
    @GetMapping("/call-logs/page")
    public Result<IPage<AiModelCallLogVO>> pageCallLogs(AiModelCallLogQueryDTO query) {
        return Result.build(adminAiModelService.pageCallLogs(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询模型成本统计。
     *
     * @param query 查询条件
     * @return 成本统计
     */
    @GetMapping("/call-logs/stats")
    public Result<AiModelCostStatsVO> costStats(AiModelCallLogQueryDTO query) {
        return Result.build(adminAiModelService.costStats(query), ResultCodeEnum.SUCCESS);
    }
}
