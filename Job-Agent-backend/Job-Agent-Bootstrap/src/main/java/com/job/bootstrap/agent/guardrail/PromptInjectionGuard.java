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
 * 功能:Prompt注入攻击检测守护组件
 * 在用户输入进入Agent Planner之前进行快速安全扫描，拦截高风险的Prompt注入攻击
 * 属于Agent系统的第一道安全防线，专门针对OWASP LLM Top 10中的LLM01: Prompt Injection风险
 * 日期:2026/6/21
 */
@Component
public class PromptInjectionGuard {

    //高风险Prompt注入规则列表
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

    /***
     * 检查用户输入是否包含高风险Prompt注入攻击
     *
     * @param message 用户输入的原始消息
     * @return 安全检测结果，包含拦截/放行决定、风险等级、命中规则等信息
     */
    public AgentGuardrailResult check(String message) {

        // 空输入直接放行，避免与业务层的必填校验重复
        if (!StringUtils.hasText(message)) {
            return AgentGuardrailResult.allow();
        }

        // 存储所有命中的规则名称，用于审计和日志记录
        List<String> matchedRules = new ArrayList<>();
        for (Rule rule : highRiskRules) {
            // 使用find()而不是matches()，因为攻击可能隐藏在正常文本中间
            if (rule.pattern().matcher(message).find()) {
                matchedRules.add(rule.name());
            }
        }

        // 没有命中任何规则，允许通过
        if (matchedRules.isEmpty()) {
            return AgentGuardrailResult.allow();
        }

        // 构建检测结果元数据，用于安全审计和后续优化
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("inputLength", message.length());
        metadata.put("strategy", "RULE_BASED_V1");

        // 命中高风险规则，直接拦截
        return AgentGuardrailResult.builder()
                .action(AgentGuardrailAction.BLOCK) // 执行动作：拦截
                .riskType(AgentGuardrailRiskType.PROMPT_INJECTION) // 风险类型：Prompt注入
                .riskLevel(9) // 风险等级
                .message("用户输入命中 Prompt 注入高风险规则") // 内部日志消息
                .userMessage("这条消息包含绕过系统规则或工具权限的要求，我不能按这个方式执行。你可以改成正常的求职问题或具体任务。") // 给用户的友好提示
                .matchedRules(matchedRules) // 命中的规则列表
                .sanitizedText(null) // 基于规则的检测不修改原始输入
                .metadata(metadata) // 附加元数据
                .build();
    }

    //创建Prompt注入检测规则的工厂方法
    private Rule rule(String name, String regex) {
        return new Rule(name, Pattern.compile(regex));
    }

    //Prompt注入检测规则数据结构
    private record Rule(String name, Pattern pattern) {
    }
}
