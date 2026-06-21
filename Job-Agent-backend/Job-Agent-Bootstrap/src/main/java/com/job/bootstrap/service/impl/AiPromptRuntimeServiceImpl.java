package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.ai.AiRenderedPrompt;
import com.job.bootstrap.mapper.AiPromptTemplateMapper;
import com.job.bootstrap.mapper.AiPromptVersionMapper;
import com.job.bootstrap.service.AiPromptRuntimeService;
import com.job.common.entity.ai.AiModelRoute;
import com.job.common.entity.ai.AiPromptTemplate;
import com.job.common.entity.ai.AiPromptVersion;
import com.job.enums.AiConfigStatus;
import com.job.enums.AiPromptVersionStatus;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 作者:hfj
 * 功能:AI Prompt 运行时解析服务实现
 * 日期:2026/6/21
 */
@Service
@RequiredArgsConstructor
public class AiPromptRuntimeServiceImpl implements AiPromptRuntimeService {

    private static final int NOT_DELETED = 0;

    /**
     * 匹配 {{变量名}} 占位符。
     * 变量名第一版只支持字母、数字、下划线、点和中划线，避免误替换大段 Prompt 内容。
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final AiPromptTemplateMapper aiPromptTemplateMapper;
    private final AiPromptVersionMapper aiPromptVersionMapper;

    /**
     * 根据路由解析 Prompt。
     *
     * 方法步骤:
     * 1. 如果路由绑定了固定版本，优先使用固定版本，适合灰度或回滚。
     * 2. 如果没有固定版本，则根据 promptCode 找到启用模板，再选择最新已发布版本。
     * 3. 使用 {{变量名}} 进行安全的纯文本替换，不执行任何表达式。
     *
     * @param route 已选中的模型路由
     * @param variables Prompt 变量
     * @return 已渲染 Prompt
     */
    @Override
    public AiRenderedPrompt renderPrompt(AiModelRoute route, Map<String, Object> variables) {
        if (route == null || !StringUtils.hasText(route.getPromptCode())) {
            throw new BizException("模型路由没有绑定 Prompt");
        }

        AiPromptVersion promptVersion = route.getPromptVersionId() == null
                ? resolvePublishedVersion(route.getPromptCode(), variables)
                : resolveFixedVersion(route.getPromptVersionId());

        String systemPrompt = renderVariables(promptVersion.getContent(), variables);
        return new AiRenderedPrompt(route, promptVersion, systemPrompt);
    }

    /**
     * 查询路由固定版本。
     *
     * @param promptVersionId Prompt 版本 ID
     * @return Prompt 版本
     */
    private AiPromptVersion resolveFixedVersion(Long promptVersionId) {
        AiPromptVersion version = aiPromptVersionMapper.selectById(promptVersionId);
        if (version == null || !Integer.valueOf(NOT_DELETED).equals(version.getIsDeleted())) {
            throw new BizException("模型路由绑定的 Prompt 版本不存在");
        }
        return version;
    }

    /**
     * 查询最新已发布版本。
     *
     * @param promptCode Prompt 编码
     * @param variables Prompt 变量
     * @return Prompt 版本
     */
    private AiPromptVersion resolvePublishedVersion(String promptCode, Map<String, Object> variables) {
        AiPromptTemplate template = aiPromptTemplateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getPromptCode, promptCode)
                .eq(AiPromptTemplate::getStatus, AiConfigStatus.ACTIVE.name())
                .eq(AiPromptTemplate::getIsDeleted, NOT_DELETED));
        if (template == null) {
            throw new BizException("Prompt 模板不存在或未启用：" + promptCode);
        }

        List<AiPromptVersion> versions = aiPromptVersionMapper.selectList(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getTemplateId, template.getId())
                .eq(AiPromptVersion::getStatus, AiPromptVersionStatus.PUBLISHED.name())
                .eq(AiPromptVersion::getIsDeleted, NOT_DELETED));
        if (versions.isEmpty()) {
            throw new BizException("Prompt 模板没有已发布版本：" + promptCode);
        }

        /*
         * 第一版 A/B 选择规则:
         * - 如果版本配置了 abGroup，并且变量里传入 abGroup，则只命中相同分组。
         * - 没传 abGroup 时不强制过滤，避免因为后台刚配置分组导致线上不可用。
         */
        String abGroup = variables == null || variables.get("abGroup") == null
                ? null
                : String.valueOf(variables.get("abGroup"));
        return versions.stream()
                .filter(version -> !StringUtils.hasText(abGroup)
                        || !StringUtils.hasText(version.getAbGroup())
                        || abGroup.equals(version.getAbGroup()))
                .max(Comparator
                        .comparing(AiPromptVersion::getPublishTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AiPromptVersion::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BizException("没有命中的 Prompt 已发布版本：" + promptCode));
    }

    /**
     * 渲染 Prompt 变量。
     *
     * @param content Prompt 原文
     * @param variables 变量 Map
     * @return 渲染后的 Prompt
     */
    private String renderVariables(String content, Map<String, Object> variables) {
        if (!StringUtils.hasText(content) || variables == null || variables.isEmpty()) {
            return content;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);

            /*
             * 找不到变量时保留原占位符。
             * 这样后台管理人员能在测试输出里快速发现变量名拼错，而不是静默变成空字符串。
             */
            String replacement = value == null ? matcher.group(0) : String.valueOf(value);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
