package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AdminAiPromptService;
import com.job.common.dto.ai.AiPromptTemplateQueryDTO;
import com.job.common.dto.ai.AiPromptTemplateSaveDTO;
import com.job.common.dto.ai.AiPromptVersionSaveDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.ai.AiPromptTemplateVO;
import com.job.common.vo.ai.AiPromptVersionVO;
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
 * 功能:后台 AI Prompt 管理接口
 * 日期:2026/6/21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai/prompts")
public class AdminAiPromptController {

    private final AdminAiPromptService adminAiPromptService;

    /**
     * 分页查询 Prompt 模板。
     *
     * @param query 查询条件
     * @return 模板分页
     */
    @GetMapping("/templates/page")
    public Result<IPage<AiPromptTemplateVO>> pageTemplates(AiPromptTemplateQueryDTO query) {
        return Result.build(adminAiPromptService.pageTemplates(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增 Prompt 模板。
     *
     * @param request 模板表单
     * @return 新增后的模板
     */
    @PostMapping("/templates")
    public Result<AiPromptTemplateVO> createTemplate(@Valid @RequestBody AiPromptTemplateSaveDTO request) {
        return Result.build(adminAiPromptService.createTemplate(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改 Prompt 模板。
     *
     * @param id 模板 ID
     * @param request 模板表单
     * @return 修改后的模板
     */
    @PutMapping("/templates/{id}")
    public Result<AiPromptTemplateVO> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody AiPromptTemplateSaveDTO request
    ) {
        return Result.build(adminAiPromptService.updateTemplate(id, request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除 Prompt 模板。
     *
     * @param id 模板 ID
     * @return 空结果
     */
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        adminAiPromptService.deleteTemplate(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询模板版本。
     *
     * @param templateId 模板 ID
     * @return 版本列表
     */
    @GetMapping("/templates/{templateId}/versions")
    public Result<List<AiPromptVersionVO>> listVersions(@PathVariable Long templateId) {
        return Result.build(adminAiPromptService.listVersions(templateId), ResultCodeEnum.SUCCESS);
    }

    /**
     * 新增 Prompt 版本。
     *
     * @param request 版本表单
     * @return 新增后的版本
     */
    @PostMapping("/versions")
    public Result<AiPromptVersionVO> createVersion(@Valid @RequestBody AiPromptVersionSaveDTO request) {
        return Result.build(adminAiPromptService.createVersion(request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改 Prompt 版本。
     *
     * @param id 版本 ID
     * @param request 版本表单
     * @return 修改后的版本
     */
    @PutMapping("/versions/{id}")
    public Result<AiPromptVersionVO> updateVersion(
            @PathVariable Long id,
            @Valid @RequestBody AiPromptVersionSaveDTO request
    ) {
        return Result.build(adminAiPromptService.updateVersion(id, request), ResultCodeEnum.SUCCESS);
    }

    /**
     * 发布 Prompt 版本。
     *
     * @param id 版本 ID
     * @return 发布后的版本
     */
    @PostMapping("/versions/{id}/publish")
    public Result<AiPromptVersionVO> publishVersion(@PathVariable Long id) {
        return Result.build(adminAiPromptService.publishVersion(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 归档 Prompt 版本。
     *
     * @param id 版本 ID
     * @return 归档后的版本
     */
    @PostMapping("/versions/{id}/archive")
    public Result<AiPromptVersionVO> archiveVersion(@PathVariable Long id) {
        return Result.build(adminAiPromptService.archiveVersion(id), ResultCodeEnum.SUCCESS);
    }
}
