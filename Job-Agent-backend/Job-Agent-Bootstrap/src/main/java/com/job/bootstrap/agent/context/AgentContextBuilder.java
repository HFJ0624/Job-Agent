package com.job.bootstrap.agent.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: Agent 上下文构建器（任务6 Context Builder）。
 *
 * 设计说明:
 * 1. 解决“总结提示词只有当前轮、无多轮历史、无预算”的问题：统一组装最近 N 轮历史 + 滚动摘要。
 * 2. 多轮历史从 ai_message 按会话加载（排除刚保存的当前轮），入库前原文可能含敏感信息，
 *    所以读入提示词前统一过 {@link AgentGuardrailService#maskSensitiveData} 脱敏。
 * 3. 字符预算近似 token（2 字符/token，与 AgentMemoryContextServiceImpl 一致）：历史从旧到新加入，
 *    超过 historyMaxChars 停止并标记截断。
 * 4. 窗口之外的更早历史，用 CONTEXT_SUMMARY 场景增量压缩成一段摘要，缓存到 ai_conversation.summary，
 *    以 summary_upto_message_id 记录已覆盖位置，避免重复压缩；摘要失败/场景未配则沿用旧摘要，绝不卡主流程。
 * 5. 执行结果预览统一走 previewExecution：脱敏 + 截断到 executionPreviewChars。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AgentContextProperties.class)
public class AgentContextBuilder {

    private static final String AI_SCENE_CONTEXT_SUMMARY = "CONTEXT_SUMMARY";
    private static final int NOT_DELETED = 0;

    private final AiMessageMapper aiMessageMapper;
    private final AiConversationMapper aiConversationMapper;
    private final AiModelGatewayService aiModelGatewayService;
    private final AgentGuardrailService agentGuardrailService;
    private final ObjectMapper objectMapper;
    private final AgentContextProperties properties;

    /**
     * 本轮总结可用的多轮上下文。
     *
     * @param historyText      最近 N 轮历史（预算内、已脱敏）
     * @param rollingSummary   更早历史的滚动摘要（可能为空）
     * @param historyTruncated 历史是否因预算被截断
     * @param estimatedTokens  历史+摘要的粗略 token 估算
     */
    public record BuiltContext(
            String historyText,
            String rollingSummary,
            boolean historyTruncated,
            int estimatedTokens
    ) {
    }

    /**
     * 构建多轮历史 + 滚动摘要。
     *
     * @param userId       当前用户
     * @param traceId      链路 ID（用于 CONTEXT_SUMMARY 网关调用）
     * @param conversation 当前会话（携带并回写 summary / summary_upto_message_id）
     * @return 多轮上下文
     */
    public BuiltContext build(Long userId, String traceId, AiConversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return new BuiltContext("", "", false, 0);
        }

        // 1. 加载最近窗口（多加载 1 条，用于排除刚保存的当前轮用户消息）
        List<AiMessage> recentDesc = loadRecentMessages(conversation.getId(), properties.getHistoryRounds() * 2 + 1);
        List<AiMessage> window = dropCurrentTurn(recentDesc);

        // 2. 窗口之外、尚未压缩过的旧历史 → 增量滚动摘要
        List<AiMessage> overflow = loadOverflow(conversation, window);
        String rollingSummary = refreshRollingSummary(userId, traceId, conversation, overflow);

        // 3. 预算内组装最近 N 轮历史
        HistoryText history = assembleHistory(window);
        int tokens = estimateTokens(history.text()) + estimateTokens(rollingSummary);
        return new BuiltContext(history.text(), rollingSummary, history.truncated(), tokens);
    }

    /**
     * 执行结果预览：转 JSON → 脱敏 → 截断，避免把带敏感信息/超长的工具输出整段塞进提示词。
     */
    public String previewExecution(Object executionResult) {
        if (executionResult == null) {
            return "（无执行结果）";
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(executionResult);
        } catch (Exception exception) {
            json = String.valueOf(executionResult);
        }
        return truncate(mask(json), properties.getExecutionPreviewChars());
    }

    // 加载最近 N 条消息（按创建时间倒序，最新在前）
    private List<AiMessage> loadRecentMessages(Long conversationId, int limit) {
        return aiMessageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .eq(AiMessage::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AiMessage::getCreateTime)
                        .orderByDesc(AiMessage::getId)
                        .last("LIMIT " + Math.max(1, limit))
        );
    }

    // 去掉最新一条（刚保存的当前轮用户消息，已在【当前用户输入】），其余反转成时间正序
    private List<AiMessage> dropCurrentTurn(List<AiMessage> recentDesc) {
        if (recentDesc.size() <= 1) {
            return List.of();
        }
        List<AiMessage> window = new ArrayList<>(recentDesc.subList(1, recentDesc.size()));
        Collections.reverse(window);
        return window;
    }

    // 窗口之外、ID 大于已摘要位置的旧消息（最新在前，限量扫描）
    private List<AiMessage> loadOverflow(AiConversation conversation, List<AiMessage> window) {
        if (window.isEmpty() || window.get(0).getId() == null) {
            return List.of();
        }
        Long floorId = window.get(0).getId();
        Long summarizedUpto = conversation.getSummaryUptoMessageId();
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversation.getId())
                .eq(AiMessage::getIsDeleted, NOT_DELETED)
                .lt(AiMessage::getId, floorId)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + Math.max(1, properties.getOverflowScanLimit()));
        if (summarizedUpto != null) {
            wrapper.gt(AiMessage::getId, summarizedUpto);
        }
        return aiMessageMapper.selectList(wrapper);
    }

    // 增量滚动摘要：待压缩消息够多才调 CONTEXT_SUMMARY，把「旧摘要 + 新旧消息」压成一段；失败沿用旧摘要
    private String refreshRollingSummary(Long userId, String traceId, AiConversation conversation, List<AiMessage> overflowRecentFirst) {
        if (overflowRecentFirst.size() < properties.getOverflowThreshold()) {
            return cap(conversation.getSummary());
        }
        List<AiMessage> chronological = new ArrayList<>(overflowRecentFirst);
        Collections.reverse(chronological);
        try {
            String output = aiModelGatewayService.chat(
                    AI_SCENE_CONTEXT_SUMMARY,
                    AiModelTier.LIGHT, // 摘要压缩是轻任务，走轻量档（任务 G1）
                    buildSummaryVariables(conversation.getSummary()),
                    buildSummaryMessage(chronological, conversation.getSummary()),
                    userId,
                    traceId
            );
            String cleaned = cleanSummary(output);
            if (StringUtils.hasText(cleaned)) {
                conversation.setSummary(cap(cleaned));
                conversation.setSummaryUptoMessageId(chronological.get(chronological.size() - 1).getId());
                aiConversationMapper.updateById(conversation);
            }
        } catch (Exception exception) {
            log.debug("CONTEXT_SUMMARY 滚动摘要降级，沿用旧摘要，convId={}, error={}",
                    conversation.getId(), exception.getMessage());
        }
        return cap(conversation.getSummary());
    }

    private Map<String, Object> buildSummaryVariables(String existingSummary) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("existingSummary", StringUtils.hasText(existingSummary) ? existingSummary : "（暂无）");
        variables.put("summaryMaxChars", properties.getSummaryMaxChars());
        return variables;
    }

    private String buildSummaryMessage(List<AiMessage> chronological, String existingSummary) {
        StringBuilder dialogue = new StringBuilder();
        for (AiMessage msg : chronological) {
            dialogue.append(roleLabel(msg.getRole())).append(": ")
                    .append(truncate(mask(msg.getContent()), properties.getPerMessageMaxChars()))
                    .append('\n');
        }
        return """
                你是对话历史压缩器。请把下面这段更早的对话历史，合并进已有的滚动摘要，输出一段更新后的简短中文摘要。

                规则:
                1. 只输出摘要正文，不要解释、Markdown 或多余文本。
                2. 保留对求职有帮助的关键事实（目标岗位、城市、偏好、已确认的信息），忽略寒暄与重复。
                3. 摘要尽量控制在 %d 字以内。

                已有滚动摘要:
                %s

                需要合并的对话历史:
                %s
                """.formatted(properties.getSummaryMaxChars(),
                StringUtils.hasText(existingSummary) ? existingSummary : "（暂无）", dialogue);
    }

    // 预算内组装历史：从旧到新加入，超过 historyMaxChars 停止并标记截断
    private HistoryText assembleHistory(List<AiMessage> window) {
        StringBuilder builder = new StringBuilder();
        boolean truncated = false;
        int index = 1;
        for (AiMessage msg : window) {
            String line = index + ". " + roleLabel(msg.getRole()) + ": "
                    + truncate(mask(msg.getContent()), properties.getPerMessageMaxChars()) + "\n";
            if (builder.length() + line.length() > properties.getHistoryMaxChars()) {
                truncated = true;
                break;
            }
            builder.append(line);
            index++;
        }
        return new HistoryText(builder.toString().trim(), truncated);
    }

    private String cleanSummary(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String text = output.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:\\w+)?\\s*", "");
            text = text.replaceFirst("(?s)\\s*```$", "");
        }
        return text.replaceAll("^[\"“]|[\"”]$", "").trim();
    }

    private String roleLabel(String role) {
        if (role == null) {
            return "消息";
        }
        return switch (role) {
            case "USER" -> "用户";
            case "ASSISTANT" -> "助手";
            case "SYSTEM" -> "系统";
            case "TOOL" -> "工具";
            default -> role;
        };
    }

    private String mask(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return String.valueOf(agentGuardrailService.maskSensitiveData(text));
    }

    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    private String cap(String value) {
        return truncate(value, properties.getSummaryMaxChars());
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private record HistoryText(String text, boolean truncated) {
    }
}
