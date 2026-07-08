package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.AiPromptTemplateMapper;
import com.job.bootstrap.mapper.AiPromptVersionMapper;
import com.job.bootstrap.service.AdminAiPromptService;
import com.job.common.dto.ai.AiPromptTemplateQueryDTO;
import com.job.common.dto.ai.AiPromptTemplateSaveDTO;
import com.job.common.dto.ai.AiPromptVersionSaveDTO;
import com.job.common.entity.ai.AiPromptTemplate;
import com.job.common.entity.ai.AiPromptVersion;
import com.job.common.vo.ai.AiPromptTemplateVO;
import com.job.common.vo.ai.AiPromptVersionVO;
import com.job.enums.AiConfigStatus;
import com.job.enums.AiPromptVersionStatus;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 后台 AI Prompt 管理服务实现。
 *
 * <p>核心职责：为后台运营人员提供 AI Prompt 模板及版本的 CRUD 管理能力，
 * 支持模板按场景编码和状态检索、版本迭代、发布与归档，
 * 保证线上模型路由始终命中稳定可用的 Prompt 版本。</p>
 *
 * <p>所属业务模块：AI 基础设施 - Prompt 管理</p>
 *
 * <p>主要调用链：
 * AdminAiPromptController → {@link AdminAiPromptServiceImpl} →
 * AiPromptTemplateMapper / AiPromptVersionMapper → 返回模板或版本 VO</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link AiPromptTemplateMapper} 管理 Prompt 模板 CRUD</li>
 *   <li>依赖 {@link AiPromptVersionMapper} 管理 Prompt 版本迭代、发布与归档</li>
 *   <li>发布的 Prompt 版本会被 {@link AdminAiModelServiceImpl} 中的模型路由引用</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>Prompt 编码全局唯一，创建和修改时做唯一性校验。</li>
 *   <li>版本号在单个模板下唯一，支持同一模板的多版本并行管理。</li>
 *   <li>发布版本时会自动将同模板下其它已发布版本归档，确保路由只会命中一个稳定版本。</li>
 *   <li>版本支持灰度百分比和 AB 分组，用于线上渐进式验证新 Prompt 效果。</li>
 * </ul></p>
 *
 * @author hfj
 * @since 2026/6/21
 */
@Service
@RequiredArgsConstructor
public class AdminAiPromptServiceImpl implements AdminAiPromptService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    private final AiPromptTemplateMapper aiPromptTemplateMapper;
    private final AiPromptVersionMapper aiPromptVersionMapper;

    /**
     * 分页查询 Prompt 模板。
     *
     * 方法步骤:
     * 1. 规范分页参数，避免前端传入过大 pageSize 拖慢后台。
     * 2. 根据编码、场景和状态拼接查询条件。
     * 3. Entity 转 VO，保持接口返回结构稳定。
     *
     * @param query 查询条件
     * @return Prompt 模板分页
     */
    @Override
    public IPage<AiPromptTemplateVO> pageTemplates(AiPromptTemplateQueryDTO query) {
        Page<AiPromptTemplate> page = new Page<>(safePageNum(query.getPageNum()), safePageSize(query.getPageSize()));
        LambdaQueryWrapper<AiPromptTemplate> wrapper = new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(query.getPromptCode())) {
            wrapper.like(AiPromptTemplate::getPromptCode, query.getPromptCode().trim());
        }
        if (StringUtils.hasText(query.getSceneCode())) {
            wrapper.like(AiPromptTemplate::getSceneCode, query.getSceneCode().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AiPromptTemplate::getStatus, query.getStatus().trim());
        }

        wrapper.orderByDesc(AiPromptTemplate::getCreateTime);
        return aiPromptTemplateMapper.selectPage(page, wrapper).convert(AiPromptTemplateVO::from);
    }

    /**
     * 创建 Prompt 模板。
     *
     * <p>创建前校验 Prompt 编码全局唯一性。</p>
     *
     * @param request 模板保存表单
     * @return 创建后的模板 VO
     * @throws BizException 当 Prompt 编码已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptTemplateVO createTemplate(AiPromptTemplateSaveDTO request) {
        String promptCode = requireText(request.getPromptCode(), "Prompt 编码不能为空");
        if (existsTemplateCode(promptCode, null)) {
            throw new BizException("Prompt 编码已经存在");
        }

        Date now = new Date();
        AiPromptTemplate template = new AiPromptTemplate();
        fillTemplate(template, request);
        template.setIsDeleted(NOT_DELETED);
        template.setCreateTime(now);
        template.setUpdateTime(now);
        aiPromptTemplateMapper.insert(template);
        return AiPromptTemplateVO.from(template);
    }

    /**
     * 修改 Prompt 模板。
     *
     * <p>修改前校验 Prompt 编码全局唯一性（排除自身）。</p>
     *
     * @param id 模板 ID
     * @param request 模板保存表单
     * @return 修改后的模板 VO
     * @throws BizException 当模板不存在或编码已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptTemplateVO updateTemplate(Long id, AiPromptTemplateSaveDTO request) {
        AiPromptTemplate template = getTemplateRequired(id);
        String promptCode = requireText(request.getPromptCode(), "Prompt 编码不能为空");
        if (existsTemplateCode(promptCode, id)) {
            throw new BizException("Prompt 编码已经存在");
        }

        fillTemplate(template, request);
        template.setUpdateTime(new Date());
        aiPromptTemplateMapper.updateById(template);
        return AiPromptTemplateVO.from(getTemplateRequired(id));
    }

    /**
     * 逻辑删除 Prompt 模板。
     *
     * @param id 模板 ID
     * @throws BizException 当模板不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        AiPromptTemplate template = getTemplateRequired(id);
        template.setIsDeleted(DELETED);
        template.setStatus(AiConfigStatus.DISABLED.name());
        template.setUpdateTime(new Date());
        aiPromptTemplateMapper.updateById(template);
    }

    /**
     * 查询某个模板下的全部版本列表。
     *
     * @param templateId 模板 ID
     * @return 该模板下的版本列表，按发布时间倒序、创建时间倒序排列
     * @throws BizException 当模板不存在时抛出
     */
    @Override
    public List<AiPromptVersionVO> listVersions(Long templateId) {
        getTemplateRequired(templateId);
        return aiPromptVersionMapper.selectList(new LambdaQueryWrapper<AiPromptVersion>()
                        .eq(AiPromptVersion::getTemplateId, templateId)
                        .eq(AiPromptVersion::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AiPromptVersion::getPublishTime)
                        .orderByDesc(AiPromptVersion::getCreateTime))
                .stream()
                .map(AiPromptVersionVO::from)
                .toList();
    }

    /**
     * 创建 Prompt 版本。
     *
     * <p>创建前校验所属模板存在，且版本号在该模板下唯一。</p>
     *
     * @param request 版本保存表单
     * @return 创建后的版本 VO
     * @throws BizException 当模板不存在或版本号已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptVersionVO createVersion(AiPromptVersionSaveDTO request) {
        getTemplateRequired(request.getTemplateId());
        String versionNo = requireText(request.getVersionNo(), "版本号不能为空");
        if (existsVersionNo(request.getTemplateId(), versionNo, null)) {
            throw new BizException("该模板下版本号已经存在");
        }

        Date now = new Date();
        AiPromptVersion version = new AiPromptVersion();
        fillVersion(version, request);
        version.setIsDeleted(NOT_DELETED);
        version.setCreateTime(now);
        version.setUpdateTime(now);
        aiPromptVersionMapper.insert(version);
        return AiPromptVersionVO.from(version);
    }

    /**
     * 修改 Prompt 版本。
     *
     * <p>修改前校验所属模板存在，且版本号在该模板下唯一（排除自身）。</p>
     *
     * @param id 版本 ID
     * @param request 版本保存表单
     * @return 修改后的版本 VO
     * @throws BizException 当版本、模板不存在或版本号已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptVersionVO updateVersion(Long id, AiPromptVersionSaveDTO request) {
        AiPromptVersion version = getVersionRequired(id);
        getTemplateRequired(request.getTemplateId());
        String versionNo = requireText(request.getVersionNo(), "版本号不能为空");
        if (existsVersionNo(request.getTemplateId(), versionNo, id)) {
            throw new BizException("该模板下版本号已经存在");
        }

        fillVersion(version, request);
        version.setUpdateTime(new Date());
        aiPromptVersionMapper.updateById(version);
        return AiPromptVersionVO.from(getVersionRequired(id));
    }

    /**
     * 发布 Prompt 版本。
     *
     * <p>方法步骤：</p>
     * <ol>
     *   <li>查询待发布版本，并确认所属模板可用。</li>
     *   <li>将同模板下其它已发布版本归档，保证默认路由只会命中一个稳定版本。</li>
     *   <li>将当前版本置为 PUBLISHED，并记录发布时间。</li>
     * </ol>
     *
     * @param id 版本 ID
     * @return 发布后的版本 VO
     * @throws BizException 当版本或模板不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptVersionVO publishVersion(Long id) {
        AiPromptVersion version = getVersionRequired(id);
        getTemplateRequired(version.getTemplateId());

        Date now = new Date();
        List<AiPromptVersion> publishedVersions = aiPromptVersionMapper.selectList(
                new LambdaQueryWrapper<AiPromptVersion>()
                        .eq(AiPromptVersion::getTemplateId, version.getTemplateId())
                        .eq(AiPromptVersion::getStatus, AiPromptVersionStatus.PUBLISHED.name())
                        .eq(AiPromptVersion::getIsDeleted, NOT_DELETED)
        );
        for (AiPromptVersion publishedVersion : publishedVersions) {
            if (!publishedVersion.getId().equals(id)) {
                publishedVersion.setStatus(AiPromptVersionStatus.ARCHIVED.name());
                publishedVersion.setUpdateTime(now);
                aiPromptVersionMapper.updateById(publishedVersion);
            }
        }

        version.setStatus(AiPromptVersionStatus.PUBLISHED.name());
        version.setPublishTime(now);
        version.setUpdateTime(now);
        aiPromptVersionMapper.updateById(version);
        return AiPromptVersionVO.from(getVersionRequired(id));
    }

    /**
     * 归档 Prompt 版本。
     *
     * @param id 版本 ID
     * @return 归档后的版本 VO
     * @throws BizException 当版本不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptVersionVO archiveVersion(Long id) {
        AiPromptVersion version = getVersionRequired(id);
        version.setStatus(AiPromptVersionStatus.ARCHIVED.name());
        version.setUpdateTime(new Date());
        aiPromptVersionMapper.updateById(version);
        return AiPromptVersionVO.from(getVersionRequired(id));
    }

    /**
     * 填充模板字段。
     *
     * @param template 模板实体
     * @param request 表单参数
     */
    private void fillTemplate(AiPromptTemplate template, AiPromptTemplateSaveDTO request) {
        template.setPromptCode(requireText(request.getPromptCode(), "Prompt 编码不能为空"));
        template.setPromptName(requireText(request.getPromptName(), "Prompt 名称不能为空"));
        template.setSceneCode(requireText(request.getSceneCode(), "业务场景不能为空"));
        template.setDescription(trimToNull(request.getDescription()));
        template.setStatus(normalizeConfigStatus(request.getStatus()));
    }

    /**
     * 填充版本字段。
     *
     * @param version 版本实体
     * @param request 表单参数
     */
    private void fillVersion(AiPromptVersion version, AiPromptVersionSaveDTO request) {
        version.setTemplateId(request.getTemplateId());
        version.setVersionNo(requireText(request.getVersionNo(), "版本号不能为空"));
        version.setTitle(requireText(request.getTitle(), "版本标题不能为空"));
        version.setContent(requireText(request.getContent(), "Prompt 内容不能为空"));
        version.setVariablesJson(trimToNull(request.getVariablesJson()));
        version.setStatus(normalizeVersionStatus(request.getStatus()));
        version.setGrayPercent(normalizePercent(request.getGrayPercent()));
        version.setAbGroup(trimToNull(request.getAbGroup()));
    }

    private AiPromptTemplate getTemplateRequired(Long id) {
        AiPromptTemplate template = aiPromptTemplateMapper.selectById(id);
        if (template == null || Integer.valueOf(DELETED).equals(template.getIsDeleted())) {
            throw new BizException("Prompt 模板不存在");
        }
        return template;
    }

    private AiPromptVersion getVersionRequired(Long id) {
        AiPromptVersion version = aiPromptVersionMapper.selectById(id);
        if (version == null || Integer.valueOf(DELETED).equals(version.getIsDeleted())) {
            throw new BizException("Prompt 版本不存在");
        }
        return version;
    }

    private boolean existsTemplateCode(String promptCode, Long excludeId) {
        LambdaQueryWrapper<AiPromptTemplate> wrapper = new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getPromptCode, promptCode)
                .eq(AiPromptTemplate::getIsDeleted, NOT_DELETED);
        if (excludeId != null) {
            wrapper.ne(AiPromptTemplate::getId, excludeId);
        }
        return aiPromptTemplateMapper.selectCount(wrapper) > 0;
    }

    private boolean existsVersionNo(Long templateId, String versionNo, Long excludeId) {
        LambdaQueryWrapper<AiPromptVersion> wrapper = new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getTemplateId, templateId)
                .eq(AiPromptVersion::getVersionNo, versionNo)
                .eq(AiPromptVersion::getIsDeleted, NOT_DELETED);
        if (excludeId != null) {
            wrapper.ne(AiPromptVersion::getId, excludeId);
        }
        return aiPromptVersionMapper.selectCount(wrapper) > 0;
    }

    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? 1L : pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return 10L;
        }
        return Math.min(pageSize, 100L);
    }

    private String normalizeConfigStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return AiConfigStatus.ACTIVE.name();
        }
        return AiConfigStatus.DISABLED.name().equals(status.trim())
                ? AiConfigStatus.DISABLED.name()
                : AiConfigStatus.ACTIVE.name();
    }

    private String normalizeVersionStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return AiPromptVersionStatus.DRAFT.name();
        }
        try {
            return AiPromptVersionStatus.valueOf(status.trim()).name();
        } catch (IllegalArgumentException exception) {
            throw new BizException("Prompt 版本状态不合法：" + status);
        }
    }

    private Integer normalizePercent(Integer percent) {
        if (percent == null) {
            return 100;
        }
        return Math.max(0, Math.min(percent, 100));
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
