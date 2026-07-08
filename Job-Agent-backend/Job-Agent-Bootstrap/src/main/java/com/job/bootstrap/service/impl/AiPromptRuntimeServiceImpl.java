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
 * AI Prompt 运行时解析服务实现，负责按路由解析并渲染最终 Prompt。
 *
 * <p>核心职责：
 * 接收 AiModelGatewayService 传来的模型路由与变量 Map，根据路由是否绑定固定版本，
 * 选择固定版本或最新已发布版本，使用 {{变量名}} 占位符进行纯文本替换后输出最终 Prompt。
 * 不执行任何表达式，避免 Prompt 注入风险。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 AI Prompt 运行时（Prompt 渲染层）。</p>
 *
 * <p>主要调用链：
 * AiModelGatewayServiceImpl.resolveRoute -> AiPromptRuntimeService.renderPrompt
 * -> resolveFixedVersion / resolvePublishedVersion（版本选择）
 * -> renderVariables（{{变量名}} 替换）
 * -> 返回 AiRenderedPrompt 给 Gateway 调用大模型</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>调用方为 AiModelGatewayServiceImpl，在每次模型调用前完成 Prompt 渲染；</li>
 *   <li>路由绑定固定版本时优先使用固定版本，适合灰度或回滚；</li>
 *   <li>未绑定固定版本时按 publishTime + createTime 选择最新已发布版本，支持 A/B 分组；</li>
 *   <li>变量缺失时保留原占位符，便于后台管理人员发现变量名拼错。</li>
 * </ul></p>
 *
 * 作者: hfj
 * 日期: 2026/6/21
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
     * 根据模型路由解析并渲染最终 Prompt。
     *
     * <p>核心处理流程：
     * 1. 校验路由非空且绑定了 promptCode，缺失时抛 BizException 中断调用；
     * 2. 路由绑定 promptVersionId 时优先使用固定版本，适合灰度或回滚；
     * 3. 未绑定固定版本时按 promptCode 找启用模板，再选择最新已发布版本；
     * 4. 使用 {{变量名}} 进行安全的纯文本替换，不执行任何表达式；
     * 5. 返回 AiRenderedPrompt，包含路由、版本与渲染后 systemPrompt。</p>
     *
     * @param route     已选中的模型路由，提供 promptCode 与可选 promptVersionId
     * @param variables Prompt 变量 Map，key 与占位符变量名一一对应
     * @return 已渲染 Prompt，包含路由、版本与最终 systemPrompt
     * @throws BizException 路由未绑定 promptCode 或版本不存在时抛出
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
     * 查询路由绑定的固定 Prompt 版本，用于灰度或回滚场景。
     *
     * @param promptVersionId Prompt 版本 ID
     * @return Prompt 版本实体
     * @throws BizException 版本不存在或已删除时抛出
     */
    private AiPromptVersion resolveFixedVersion(Long promptVersionId) {
        AiPromptVersion version = aiPromptVersionMapper.selectById(promptVersionId);
        if (version == null || !Integer.valueOf(NOT_DELETED).equals(version.getIsDeleted())) {
            throw new BizException("模型路由绑定的 Prompt 版本不存在");
        }
        return version;
    }

    /**
     * 按 promptCode 查询启用模板下的最新已发布版本，支持 A/B 分组命中。
     *
     * <p>核心处理流程：
     * 1. 按 promptCode 查询状态为 ACTIVE 的模板，缺失时抛 BizException；
     * 2. 查询该模板下所有 PUBLISHED 状态版本；
     * 3. 第一版 A/B 选择规则：版本配置了 abGroup 且变量传入 abGroup 时只命中相同分组，
     *    未传 abGroup 时不强制过滤，避免后台刚配置分组导致线上不可用；
     * 4. 按 publishTime + createTime 倒序选择最新版本。</p>
     *
     * @param promptCode Prompt 编码
     * @param variables  Prompt 变量 Map，用于读取 abGroup 进行 A/B 命中
     * @return 命中的最新已发布版本
     * @throws BizException 模板不存在、无已发布版本或无命中版本时抛出
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
     * 使用 {{变量名}} 占位符进行纯文本替换，不执行任何表达式。
     *
     * <p>说明：找不到变量时保留原占位符，让后台管理人员能在测试输出里快速发现变量名拼错，
     * 而不是静默变成空字符串。变量名第一版只支持字母、数字、下划线、点和中划线，
     * 避免误替换大段 Prompt 内容。</p>
     *
     * @param content   Prompt 原文
     * @param variables 变量 Map
     * @return 渲染后的 Prompt，无变量时原样返回
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
