package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.ai.AiPromptTemplateQueryDTO;
import com.job.common.dto.ai.AiPromptTemplateSaveDTO;
import com.job.common.dto.ai.AiPromptVersionSaveDTO;
import com.job.common.vo.ai.AiPromptTemplateVO;
import com.job.common.vo.ai.AiPromptVersionVO;

import java.util.List;

/**
 * 后台 AI Prompt 管理服务接口。
 *
 * <p>核心职责：为运营和研发人员提供 Prompt 模板的生命周期管理，包括模板增删改查、版本迭代、发布与归档。</p>
 *
 * <p>所属业务模块：后台管理 - AI 基础设施</p>
 *
 * <p>主要调用链：
 * AdminAiPromptController -&gt; AdminAiPromptService -&gt; AdminAiPromptServiceImpl -&gt; AiPromptTemplateRepository / AiPromptVersionRepository</p>
 */
public interface AdminAiPromptService {

    /**
     * 分页查询 Prompt 模板。
     *
     * @param query 查询条件
     * @return 模板分页
     */
    IPage<AiPromptTemplateVO> pageTemplates(AiPromptTemplateQueryDTO query);

    /**
     * 创建 Prompt 模板。
     *
     * @param request 模板保存参数
     * @return 创建后的模板
     */
    AiPromptTemplateVO createTemplate(AiPromptTemplateSaveDTO request);

    /**
     * 更新 Prompt 模板。
     *
     * @param id 模板 ID
     * @param request 模板保存参数
     * @return 更新后的模板
     */
    AiPromptTemplateVO updateTemplate(Long id, AiPromptTemplateSaveDTO request);

    /**
     * 删除 Prompt 模板。
     *
     * @param id 模板 ID
     */
    void deleteTemplate(Long id);

    /**
     * 查询指定模板下的所有版本。
     *
     * @param templateId 模板 ID
     * @return 版本列表
     */
    List<AiPromptVersionVO> listVersions(Long templateId);

    /**
     * 创建 Prompt 版本。
     *
     * @param request 版本保存参数
     * @return 创建后的版本
     */
    AiPromptVersionVO createVersion(AiPromptVersionSaveDTO request);

    /**
     * 更新 Prompt 版本。
     *
     * @param id 版本 ID
     * @param request 版本保存参数
     * @return 更新后的版本
     */
    AiPromptVersionVO updateVersion(Long id, AiPromptVersionSaveDTO request);

    /**
     * 发布 Prompt 版本，发布后将作为线上生效版本。
     *
     * @param id 版本 ID
     * @return 发布后的版本
     */
    AiPromptVersionVO publishVersion(Long id);

    /**
     * 归档 Prompt 版本，归档后不再参与线上路由。
     *
     * @param id 版本 ID
     * @return 归档后的版本
     */
    AiPromptVersionVO archiveVersion(Long id);
}
