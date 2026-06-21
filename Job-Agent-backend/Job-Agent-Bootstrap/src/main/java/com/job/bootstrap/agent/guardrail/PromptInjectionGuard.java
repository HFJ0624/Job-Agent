package com.job.bootstrap.agent.guardrail;

import com.job.common.vo.agent.AgentGuardrailResult;
import com.job.enums.AgentGuardrailAction;
import com.job.enums.AgentGuardrailRiskType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 作者:hfj
 * 功能:Prompt 注入检测
 * 日期:2026/6/21
 */
@Component
public class PromptInjectionGuard {

    private final List<Rule> highRiskRules = List.of(
            rule("IGNORE_PREVIOUS_INSTRUCTIONS", "(?i)(ignore|forget|discard)\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|rules|messages)"),
            rule("REVEAL_SYSTEM_PROMPT", "(?i)(show|print|reveal|leak|dump).{0,20}(system prompt|developer message|hidden prompt|internal prompt)"),
            rule("BYPASS_GUARDRAILS", "(?i)(bypass|disable|override).{0,20}(guardrail|safety|permission|confirmation|tool guard)"),
            rule("FAKE_TOOL_RESULT", "(?i)(pretend|fake|simulate).{0,20}(tool result|tool response|function result)"),
            rule("NO_TRACE_LOG", "(?i)(do not|don't|never).{0,20}(log|record|trace)"),
            rule("ZH_IGNORE_INSTRUCTIONS", "忽略.{0,12}(之前|以上|系统|开发者).{0,12}(指令|规则|消息)"),
            rule("ZH_REVEAL_PROMPT", "(输出|显示|泄露|打印).{0,12}(系统提示词|系统指令|开发者消息|隐藏提示词)"),
            rule("ZH_BYPASS_TOOL_GUARD", "(绕过|关闭|禁用).{0,12}(权限|确认|工具校验|护栏|安全策略)"),
            rule("ZH_FAKE_TOOL_RESULT", "(伪造|假装|模拟).{0,12}(工具返回|工具结果|函数结果)"),
            rule("ZH_NO_TRACE_LOG", "(不要|禁止).{0,12}(记录|写入).{0,12}(日志|Trace|trace)")
    );

    /**
     * 检查用户输入是否包含 Prompt 注入。
     *
     * 方法步骤:
     * 1. 空文本直接放行，真正的必填校验由 DTO 和业务入口处理。
     * 2. 先匹配高风险规则，例如要求泄露系统提示词、绕过工具确认、伪造工具结果。
     * 3. 如果命中高风险规则，直接返回 BLOCK，避免 Planner 被恶意目标污染。
     * 4. 如果没有命中，返回 ALLOW，让后续 Planner/Executor 正常工作。
     */
    public AgentGuardrailResult check(String message) {
        if (!StringUtils.hasText(message)) {
            return AgentGuardrailResult.allow();
        }

        List<String> matchedRules = new ArrayList<>();
        for (Rule rule : highRiskRules) {
            if (rule.pattern().matcher(message).find()) {
                matchedRules.add(rule.name());
            }
        }

        if (matchedRules.isEmpty()) {
            return AgentGuardrailResult.allow();
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("inputLength", message.length());
        metadata.put("strategy", "RULE_BASED_V1");

        return AgentGuardrailResult.builder()
                .action(AgentGuardrailAction.BLOCK)
                .riskType(AgentGuardrailRiskType.PROMPT_INJECTION)
                .riskLevel(9)
                .message("用户输入命中 Prompt 注入高风险规则")
                .userMessage("这条消息包含绕过系统规则或工具权限的要求，我不能按这个方式执行。你可以改成正常的求职问题或具体任务。")
                .matchedRules(matchedRules)
                .sanitizedText(null)
                .metadata(metadata)
                .build();
    }

    private Rule rule(String name, String regex) {
        return new Rule(name, Pattern.compile(regex));
    }

    private record Rule(String name, Pattern pattern) {
    }
}
