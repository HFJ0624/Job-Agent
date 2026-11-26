package com.job.bootstrap.agent.application.chat.service;

import com.job.bootstrap.ai.TokenConsumer;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能: 新链路的"流式答复"生成（方案 B）。
 *
 * <p>背景：复合/简历/搜索三类入口先产出**已校验的结构化结果**，再把整段答复一次性推给前端，
 * 观感是"想很久然后整段蹦出来"。这里复用网关的 AGENT_SUMMARY 流式能力，
 * 把已校验结果改写成自然语言并逐 token 推送。</p>
 *
 * <p>边界：只做表达改写，不允许新增事实——输入里已包含服务端可信字段；
 * 失败由调用方降级为整段输出，不改变主流程成败。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentReplyStreamService {

    private static final String SCENE_AGENT_SUMMARY = "AGENT_SUMMARY";

    private final AiModelGatewayService aiModelGatewayService;

    /**
     * 把已校验答复流式改写给用户。
     *
     * @param groundedAnswer 服务端已校验的答复文本（唯一事实来源）
     * @param userId         当前用户
     * @param traceId        链路 ID
     * @param consumer       token 消费者（SSE）
     * @return 完整文本；失败时抛异常由调用方降级
     */
    public String stream(String groundedAnswer, Long userId, String traceId, TokenConsumer consumer) {
        String instruction = "请把下面这段已经过服务端校验的结果，改写成给用户看的自然语言答复。"
                + "只做表达优化，不得新增、推测或修改任何事实（岗位、公司、城市、薪资、分数、结论）。"
                + "保留分点结构，直接输出正文，不要解释你在做什么。\n\n" + groundedAnswer;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("currentUserInput", instruction);
        variables.put("current_user_input", instruction);
        variables.put("ragContextMessage", "");
        variables.put("rag_context_message", "");
        variables.put("historyText", "");
        variables.put("rollingSummary", "");
        variables.put("planJson", "{}");
        variables.put("plan_json", "{}");
        variables.put("executionResultJson", "{\"answer\":\"" + escape(groundedAnswer) + "\"}");
        variables.put("execution_result_json", variables.get("executionResultJson"));
        variables.put("retrievedMemoriesJson", "[]");
        variables.put("retrieved_memories_json", "[]");
        variables.put("memoryPromptContext", "-");
        variables.put("memory_prompt_context", "-");
        variables.put("summaryRequirements", "直接输出给用户的答复，保留原有结论与数字，不要新增事实。");

        return aiModelGatewayService.chatStream(
                SCENE_AGENT_SUMMARY,
                AiModelTier.PRO,
                variables,
                instruction,
                userId,
                traceId,
                consumer
        );
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
