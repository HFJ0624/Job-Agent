package com.job.bootstrap.agent.rewrite;

import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 多轮查询改写器（任务 G8）。
 *
 * 解决什么问题:
 * 1. 用户说“那第二个呢”“刚才的匹配度如何”时，消息本身不自包含——意图识别、
 *    参数抽取、RAG 检索都拿不到有效信息；
 * 2. 本组件结合会话历史把当前消息改写成自包含查询（“刚才推荐的第二个Java岗位的匹配度如何”），
 *    再喂给 Planner 和 RAG，让后续链路基于完整语义工作。
 *
 * 设计说明:
 * 1. 成本门控：消息不含指代/延续信号词时判定为自包含，直接原样返回，不花模型钱
 *    （信号词命中是“可能不完整”的启发式，漏判的代价由后续链路原有降级承担）；
 * 2. 档位：改写是轻量转换任务，走 LIGHT 档模型（任务 G1 分级路由）；
 * 3. 输出清洗：去首尾引号、去“改写后:”类前缀、长度兜底——模型啰嗦或跑题时回退原消息；
 * 4. 防御性降级：场景未配置/模型失败/输出异常一律返回原消息——改写是增强，不是新故障点；
 * 5. 观测：改写生效时记 REWRITE 事件（原消息 → 改写后），后台可统计改写命中率。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentQueryRewriter {

    private static final String AI_SCENE_QUERY_REWRITE = "QUERY_REWRITE";

    /** 改写输出长度上限：超过视为模型跑题，回退原消息。 */
    private static final int MAX_REWRITE_LENGTH = 500;

    /** 指代/延续信号词：命中才值得发起模型调用。 */
    private static final List<String> REFERENCE_SIGNALS = List.of(
            "那", "这个", "那个", "它", "他", "她", "刚才", "刚刚", "上面", "之前", "呢",
            "还有", "继续", "第二个", "第三个", "第四个", "第2个", "第3个", "第4个",
            "一样", "同样", "换一个", "重新推荐"
    );

    /** 模型输出里常见的前缀噪音，清洗时去掉。 */
    private static final List<String> OUTPUT_PREFIXES = List.of(
            "改写后:", "改写后：", "改写后的查询:", "改写后的查询：", "查询:", "查询：", "改写:"
    );

    private final AiModelGatewayService aiModelGatewayService;
    private final AgentObservationService agentObservationService;

    /**
     * 把当前消息改写为自包含查询。
     *
     * @param userId         当前用户
     * @param traceId        链路 ID
     * @param currentMessage 当前轮用户消息（已脱敏）
     * @param historyText    会话历史文本（不含当前轮；为空说明首轮或新会话）
     * @return 自包含查询；无法改写时原样返回
     */
    public String rewrite(Long userId, String traceId, String currentMessage, String historyText) {
        if (!StringUtils.hasText(currentMessage)) {
            return currentMessage;
        }
        // 首轮/无历史：没有可消解的指代，直接返回
        if (!StringUtils.hasText(historyText) || historyText.trim().length() < 10) {
            return currentMessage;
        }
        // 自包含消息：不花模型钱
        if (!containsReferenceSignal(currentMessage)) {
            return currentMessage;
        }

        try {
            String rewritten = aiModelGatewayService.chat(
                    AI_SCENE_QUERY_REWRITE,
                    AiModelTier.LIGHT, // 轻量转换任务走轻模型（任务 G1）
                    buildVariables(historyText, currentMessage),
                    buildUserMessage(historyText, currentMessage),
                    userId,
                    traceId
            );
            String sanitized = sanitize(rewritten, currentMessage);
            if (!sanitized.equals(currentMessage)) {
                recordRewriteEvent(userId, traceId, currentMessage, sanitized);
            }
            return sanitized;
        } catch (Exception exception) {
            log.warn("查询改写失败，使用原始消息继续，userId={}, error={}", userId, exception.getMessage());
            return currentMessage;
        }
    }

    private Map<String, Object> buildVariables(String historyText, String currentMessage) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("history", historyText);
        variables.put("currentMessage", currentMessage);
        variables.put("jsonFormat", "只输出改写后的查询文本本身。");
        return variables;
    }

    /*
     * 用户消息即完整指令：系统提示词走后台 QUERY_REWRITE 场景配置（含输出约束），
     * 这里把历史与当前消息作为 userMessage 传入，变量同时提供给模板渲染。
     */
    private String buildUserMessage(String historyText, String currentMessage) {
        return "会话历史:\n" + historyText + "\n\n当前用户输入: " + currentMessage;
    }

    // 输出清洗：去引号/前缀/超长；引号与前缀可能叠加（如 改写后的查询:“xxx”），循环到稳定为止
    private String sanitize(String rewritten, String originalMessage) {
        if (!StringUtils.hasText(rewritten)) {
            return originalMessage;
        }
        String text = rewritten.trim();
        String previous;
        do {
            previous = text;
            text = stripWrappingQuotes(text);
            text = stripKnownPrefix(text);
        } while (!text.equals(previous));

        if (!StringUtils.hasText(text) || text.length() > MAX_REWRITE_LENGTH) {
            return originalMessage;
        }
        return text;
    }

    private String stripWrappingQuotes(String text) {
        if (text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\""))
                        || (text.startsWith("“") && text.endsWith("”"))
                        || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private String stripKnownPrefix(String text) {
        for (String prefix : OUTPUT_PREFIXES) {
            if (text.startsWith(prefix)) {
                return text.substring(prefix.length()).trim();
            }
        }
        return text;
    }

    private boolean containsReferenceSignal(String message) {
        for (String signal : REFERENCE_SIGNALS) {
            if (message.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private void recordRewriteEvent(Long userId, String traceId, String originalMessage, String rewritten) {
        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("originalMessage", originalMessage);
        requestSnapshot.put("rewrittenMessage", rewritten);
        requestSnapshot.put("originalLength", originalMessage.length());
        requestSnapshot.put("rewrittenLength", rewritten.length());

        try {
            agentObservationService.recordEvent(AgentObservationRecord.builder()
                    .traceId(traceId)
                    .userId(userId)
                    .eventType(AgentObservationEventType.REWRITE)
                    .eventName("QueryRewrite")
                    .status(AgentObservationStatus.SUCCESS)
                    .requestSnapshot(requestSnapshot)
                    .build());
        } catch (Exception exception) {
            log.debug("查询改写观测事件记录失败，userId={}, error={}", userId, exception.getMessage());
        }
    }
}
