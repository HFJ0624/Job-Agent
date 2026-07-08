package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.common.vo.agent.AgentInboxVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent AI 日报生成器，负责将 Agent Inbox 快照转换为结构化日报。
 *
 * <p>核心职责：
 * 接收 AgentDailyReportServiceImpl 传入的 Inbox 快照，构造 Prompt 变量与 UserMessage，
 * 调用 AiModelGatewayService 统一模型网关，要求模型严格输出 JSON 并解析为 AiComposeResult。
 * 解析失败直接抛异常，不使用规则日报兜底，保证用户看到的就是模型真实输出。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Daily Report 子模块（AI 日报生成层）。</p>
 *
 * <p>主要调用链：
 * AgentDailyReportServiceImpl.generateForUser -> AgentDailyReportAiComposer.compose
 * -> buildInboxSnapshot（构造 Inbox 快照）
 * -> AiModelGatewayService.chat（sceneCode=AGENT_DAILY_REPORT）
 * -> parseResponse（JSON 解析与字段校验）
 * -> 返回 AiComposeResult 给日报 Service 落库</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>第二版日报由模型生成，但仍然把 Agent Inbox 作为事实输入，避免模型凭空编造待办；</li>
 *   <li>模型必须返回 JSON，服务端解析后再保存；解析失败直接失败，不使用规则日报兜底；</li>
 *   <li>sceneCode 固定为 AGENT_DAILY_REPORT，由后台模型与 Prompt 管理页面配置具体模板和模型；</li>
 *   <li>变量 Map 同时提供驼峰与下划线两种命名，兼容不同 Prompt 模板风格。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 第二版日报由模型生成，但仍然把 Agent Inbox 作为事实输入，避免模型凭空编造待办。
 * 2. 模型必须返回 JSON，服务端解析后再保存；解析失败直接失败，不使用规则日报兜底。
 * 3. sceneCode 固定为 AGENT_DAILY_REPORT，由后台模型与 Prompt 管理页面配置具体模板和模型。</p>
 *
 * 作者: hfj
 */
@Component
@RequiredArgsConstructor
public class AgentDailyReportAiComposer {

    public static final String SCENE_AGENT_DAILY_REPORT = "AGENT_DAILY_REPORT";

    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成 AI 日报，覆盖快照构造、模型调用与 JSON 解析全流程。
     *
     * <p>核心处理流程：
     * 1. 兜底处理 inbox 为空的情况，避免后续 NPE；
     * 2. 构造 Inbox 快照 JSON 与 Prompt 变量 Map（同时提供驼峰与下划线命名）；
     * 3. 构造 UserMessage，明确要求模型只能基于输入数据总结，输出 JSON；
     * 4. 调用统一模型网关 AiModelGatewayService.chat，sceneCode 固定为 AGENT_DAILY_REPORT；
     * 5. 解析模型返回 JSON，缺失必填字段或解析失败时抛 IllegalArgumentException。</p>
     *
     * @param userId 当前用户 ID，作为 @MemoryId 传入模型网关
     * @param inbox  今日 Inbox 快照，作为模型事实输入
     * @return AI 生成后的日报内容，包含 title、summary、content 与 contentJson
     * @throws IllegalArgumentException 模型返回为空、JSON 解析失败或必填字段缺失时抛出
     */
    public AiComposeResult compose(Long userId, AgentInboxVO inbox) {
        AgentInboxVO safeInbox = inbox == null ? new AgentInboxVO() : inbox;
        String inboxJson = toJson(buildInboxSnapshot(safeInbox));
        Map<String, Object> variables = buildVariables(safeInbox, inboxJson);
        String userMessage = buildUserMessage(inboxJson);

        /*
         * 1. 调用统一模型网关。这里不捕获异常，让服务层记录失败并返回失败。
         */
        String response = aiModelGatewayService.chat(
                SCENE_AGENT_DAILY_REPORT,
                variables,
                userMessage,
                userId,
                buildTraceId(userId)
        );

        /*
         * 2. 模型必须输出 JSON。解析失败说明 Prompt 或模型输出不符合契约，直接失败。
         */
        return parseResponse(response);
    }

    private Map<String, Object> buildVariables(AgentInboxVO inbox, String inboxJson) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("inboxJson", inboxJson);
        variables.put("inbox_json", inboxJson);
        variables.put("totalCount", inbox.getTotalCount());
        variables.put("total_count", inbox.getTotalCount());
        variables.put("highPriorityCount", inbox.getHighPriorityCount());
        variables.put("high_priority_count", inbox.getHighPriorityCount());
        variables.put("dueCount", inbox.getDueCount());
        variables.put("due_count", inbox.getDueCount());
        variables.put("outputFormat", expectedJsonFormat());
        variables.put("output_format", expectedJsonFormat());
        return variables;
    }

    private String buildUserMessage(String inboxJson) {
        return """
                请基于下面的 Agent Inbox 事实数据生成今天的求职 Agent 日报。
                要求：
                1. 只能根据输入数据总结，不要编造不存在的公司、岗位、面试或待办。
                2. 输出必须是 JSON 对象，不要 Markdown，不要解释文字。
                3. topActions 固定输出 3 条，适合作为今天最该做的 3 件事。
                4. recommendedOrder 按处理优先级排序，优先处理高优先级和已到期事项。
                5. riskAlerts 没有风险时返回空数组。

                JSON 格式：
                %s

                Agent Inbox 数据：
                %s
                """.formatted(expectedJsonFormat(), inboxJson);
    }

    private String expectedJsonFormat() {
        return """
                {
                  "title": "今日求职 Agent 日报",
                  "summary": "一句话摘要",
                  "todayFocus": ["今日重点"],
                  "riskAlerts": ["风险提醒"],
                  "recommendedOrder": ["推荐处理顺序"],
                  "topActions": ["今天最该做的 3 件事"]
                }
                """;
    }

    private AiComposeResult parseResponse(String response) {
        if (!StringUtils.hasText(response)) {
            throw new IllegalArgumentException("AI 日报生成失败：模型返回为空");
        }

        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFence(response));
            String title = requiredText(root, "title");
            String summary = requiredText(root, "summary");
            String content = buildContent(root);
            String contentJson = objectMapper.writeValueAsString(root);
            return new AiComposeResult(title, summary, content, contentJson);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("AI 日报 JSON 解析失败：" + exception.getMessage(), exception);
        }
    }

    private String buildContent(JsonNode root) {
        StringBuilder builder = new StringBuilder();
        builder.append(requiredText(root, "summary")).append("\n\n");
        appendArraySection(builder, "今日重点", root.path("todayFocus"));
        appendArraySection(builder, "风险提醒", root.path("riskAlerts"));
        appendArraySection(builder, "推荐处理顺序", root.path("recommendedOrder"));
        appendArraySection(builder, "今天最该做的 3 件事", root.path("topActions"));
        return builder.toString();
    }

    private void appendArraySection(StringBuilder builder, String title, JsonNode arrayNode) {
        builder.append("【").append(title).append("】\n");
        if (!arrayNode.isArray() || arrayNode.isEmpty()) {
            builder.append("暂无\n\n");
            return;
        }

        for (int i = 0; i < arrayNode.size(); i++) {
            String value = arrayNode.get(i).asText();
            if (StringUtils.hasText(value)) {
                builder.append(i + 1).append(". ").append(value.trim()).append("\n");
            }
        }
        builder.append("\n");
    }

    private String requiredText(JsonNode root, String fieldName) {
        String value = root.path(fieldName).asText();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("AI 日报 JSON 缺少字段：" + fieldName);
        }
        return value.trim();
    }

    private Map<String, Object> buildInboxSnapshot(AgentInboxVO inbox) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summaryText", inbox.getSummaryText());
        root.put("totalCount", inbox.getTotalCount());
        root.put("highPriorityCount", inbox.getHighPriorityCount());
        root.put("dueCount", inbox.getDueCount());
        root.put("normalCount", inbox.getNormalCount());

        List<Map<String, Object>> items = inbox.getItems() == null
                ? List.of()
                : inbox.getItems().stream().map(this::buildItemSnapshot).toList();
        root.put("items", items);
        return root;
    }

    private Map<String, Object> buildItemSnapshot(AgentInboxVO.Item item) {
        Map<String, Object> itemMap = new LinkedHashMap<>();
        itemMap.put("itemKey", item.getItemKey());
        itemMap.put("itemType", item.getItemType());
        itemMap.put("itemTypeDesc", item.getItemTypeDesc());
        itemMap.put("priority", item.getPriority());
        itemMap.put("title", item.getTitle());
        itemMap.put("description", item.getDescription());
        itemMap.put("actionText", item.getActionText());
        itemMap.put("targetPath", item.getTargetPath());
        itemMap.put("companyName", item.getCompanyName());
        itemMap.put("jobTitle", item.getJobTitle());
        itemMap.put("dueTime", item.getDueTime());
        return itemMap;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String stripMarkdownFence(String response) {
        String text = response.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json\\s*", "");
            text = text.replaceFirst("^```\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }

    private String buildTraceId(Long userId) {
        return "agent-daily-report-" + userId + "-" + UUID.randomUUID();
    }

    /**
     * AI 日报生成结果。
     */
    public record AiComposeResult(
            String title,
            String summary,
            String content,
            String contentJson
    ) {
    }
}
