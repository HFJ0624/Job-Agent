package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.ai.AiPromptTemplateQueryDTO;
import com.job.common.dto.ai.AiPromptTemplateSaveDTO;
import com.job.common.dto.ai.AiPromptVersionSaveDTO;
import com.job.common.vo.ai.AiPromptTemplateVO;
import com.job.common.vo.ai.AiPromptVersionVO;

import java.util.List;

/**
 * 作者:hfj
 * 功能:后台 AI Prompt 管理服务
 * 日期:2026/6/21
 */
public interface AdminAiPromptService {

    IPage<AiPromptTemplateVO> pageTemplates(AiPromptTemplateQueryDTO query);

    AiPromptTemplateVO createTemplate(AiPromptTemplateSaveDTO request);

    AiPromptTemplateVO updateTemplate(Long id, AiPromptTemplateSaveDTO request);

    void deleteTemplate(Long id);

    List<AiPromptVersionVO> listVersions(Long templateId);

    AiPromptVersionVO createVersion(AiPromptVersionSaveDTO request);

    AiPromptVersionVO updateVersion(Long id, AiPromptVersionSaveDTO request);

    AiPromptVersionVO publishVersion(Long id);

    AiPromptVersionVO archiveVersion(Long id);
}
