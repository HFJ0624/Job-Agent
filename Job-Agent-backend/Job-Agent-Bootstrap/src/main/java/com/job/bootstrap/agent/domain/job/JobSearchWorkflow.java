package com.job.bootstrap.agent.domain.job;

import com.job.bootstrap.agent.runtime.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.runtime.executor.AgentToolInvoker;
import com.job.bootstrap.agent.runtime.react.AgentDefinition;
import com.job.bootstrap.agent.runtime.react.AgentResult;
import com.job.bootstrap.agent.runtime.react.AgentRunContext;
import com.job.bootstrap.agent.runtime.react.AgentRunExecutionHooks;
import com.job.bootstrap.agent.runtime.react.ReActRunner;
import com.job.bootstrap.agent.runtime.react.TerminalToolContract;
import com.job.bootstrap.ai.turn.AiChatMessage;
import com.job.bootstrap.ai.turn.AiToolSpec;
import com.job.common.agent.AgentRuntimeContext;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 功能: 岗位搜索 Workflow（第一阶段四节点，含验收修复）。
 *
 * 固定骨架（文档规定）: VALIDATE_INPUT → JOB_SEARCH_REACT → VALIDATE_RESULT → RESPOND。
 *
 * 本轮验收修复（2026-11-03 外部审查 R1/R3/R4/R7）:
 * 1. R1 身份桥接：工具执行边界用**服务端可信身份**设置 AgentRuntimeContext
 *    （userId/conversationId/traceId），finally 恢复原上下文——真实工具不再因缺上下文失败；
 * 2. R4 结果边界：FOUND 要求**全部** candidateId 来自真实结果、无重复、满足硬条件，
 *    任一不合法整单 FAILED；EMPTY 要求“查询成功且合格候选池为空”；
 *    目标方向（targetRole）独立于可调整召回词，Python 岗位不能顶替 Java 目标；
 * 3. R4-4：对外答复由服务端可信结果构造，**不再追加模型 summary**（模型可编造“已投递”）；
 * 4. R7：薪资统一元/月，展示转 K/月；公司字段缺失显示“公司未知”，不编造；
 * 5. R3：续跑恢复有效候选（条件未变时播种），并刷新 system Prompt 中的任务约束。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSearchWorkflow {

    public static final String AGENT_NAME = "job-search";
    public static final String SEARCH_TOOL_ALIAS = "job_search";
    public static final String RECOMMEND_TOOL_ALIAS = "job_recommend";
    public static final String SEARCH_TOOL = "JobSearchTool.searchJobs";
    public static final String RECOMMEND_TOOL = "JobRecommendTool.recommendJobs";

    /** 对外展示的候选上限。 */
    private static final int MAX_CANDIDATES_IN_ANSWER = 5;

    private final ReActRunner reActRunner;
    private final TerminalToolContract terminalToolContract;
    private final AgentToolInvoker agentToolInvoker;
    private final JobCandidatesAdapter candidatesAdapter;

    /**
     * Workflow 输入。
     *
     * @param userId          用户ID（可信身份）
     * @param traceId         链路ID
     * @param userMessage     本轮用户输入
     * @param constraints     条件快照（已三级合并）
     * @param resumeMessages  续跑历史消息（null=新任务）
     * @param resumeTurn      续跑已用轮次
     * @param resumeToolCalls 续跑已用工具次数
     * @param startedAtMillis 本次执行开始时间
     * @param conversationId  会话ID（工具 Trace 关联，可空）
     * @param intentCode      意图编码（工具 Trace 关联，可空）
     * @param seedCandidates  续跑时可复用的有效候选（条件变化时传空）
     */
    public record WorkflowInput(
            Long userId,
            String traceId,
            String userMessage,
            SearchConstraints constraints,
            List<AiChatMessage> resumeMessages,
            int resumeTurn,
            int resumeToolCalls,
            long startedAtMillis,
            Long conversationId,
            String intentCode,
            List<JobCandidates.JobCandidateItem> seedCandidates
    ) {

        /** 兼容旧 8 参构造（无会话/意图/历史候选）。 */
        public WorkflowInput(Long userId, String traceId, String userMessage, SearchConstraints constraints,
                             List<AiChatMessage> resumeMessages, int resumeTurn, int resumeToolCalls,
                             long startedAtMillis) {
            this(userId, traceId, userMessage, constraints, resumeMessages, resumeTurn,
                    resumeToolCalls, startedAtMillis, null, null, List.of());
        }
    }

    /**
     * Workflow 输出。
     */
    public record WorkflowOutcome(
            AgentResult.Outcome outcome,
            String reasonCode,
            String answer,
            List<JobCandidates.JobCandidateItem> candidates,
            List<String> missingFields,
            String question,
            List<AiChatMessage> messages,
            int toolCallsUsed,
            int turnsUsed
    ) {
    }

    /**
     * 执行四节点工作流。
     */
    public WorkflowOutcome execute(WorkflowInput input) {
        SearchConstraints constraints = input.constraints() == null
                ? new SearchConstraints(null, null, null, null, Map.of(), null)
                : input.constraints();

        // 节点 1：输入校验（可选条件不硬性要求）
        String userMessage = StringUtils.hasText(input.userMessage()) ? input.userMessage().trim() : "";
        if (!StringUtils.hasText(userMessage)) {
            return failure("NEED_INPUT", "输入为空，请描述要找的岗位（城市、方向可选）。",
                    List.of("query"), "请告诉我你要找的岗位方向或城市。", List.of(), 0, 0);
        }

        // 节点 2：ReAct（真实工具 + 实时收集候选）
        Map<Long, JobCandidates.JobCandidateItem> pool = new LinkedHashMap<>();
        // R3：续跑播种有效候选（条件未变时由 Entry 传入；变化时为空）
        if (input.seedCandidates() != null) {
            input.seedCandidates().forEach(item -> pool.putIfAbsent(item.jobId(), item));
        }
        boolean[] anyToolSucceeded = {false};

        AgentRunContext context = buildContext(input, constraints);

        AgentResult result = reActRunner.run(
                definition(constraints),
                context,
                (javaToolName, arguments) -> executeTool(javaToolName, arguments, constraints, pool,
                        anyToolSucceeded, input),
                input.userId(),
                input.traceId()
        );

        // 节点 3+4：验真与响应
        return validateAndRespond(result, pool, anyToolSucceeded[0], context, constraints);
    }

    // 续跑时刷新 system Prompt（旧 Prompt 里的条件可能已变），并追加本轮输入
    private AgentRunContext buildContext(WorkflowInput input, SearchConstraints constraints) {
        boolean resume = input.resumeMessages() != null && !input.resumeMessages().isEmpty();
        if (!resume) {
            return new AgentRunContext(systemPrompt(constraints), input.userMessage());
        }
        AgentRunContext context = new AgentRunContext(input.resumeMessages(), input.resumeTurn(),
                input.resumeToolCalls(), input.startedAtMillis());
        // R3：条件可能已更新，替换旧 system 消息，避免模型看到过期条件
        context.replaceSystemPrompt(systemPrompt(constraints));
        context.append(AiChatMessage.user(input.userMessage()));
        return context;
    }

    /**
     * 工具执行：可信身份桥接 + 硬条件覆盖 + 统一执行器调用 + 真实候选收集。
     */
    private String executeTool(String javaToolName,
                               Map<String, Object> arguments,
                               SearchConstraints constraints,
                               Map<Long, JobCandidates.JobCandidateItem> pool,
                               boolean[] anyToolSucceeded,
                               WorkflowInput input) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (SEARCH_TOOL.equals(javaToolName)) {
            params.put("keyword", text(arguments.get("keyword")));
            params.put("page", boundedInt(arguments.get("page"), 1, 20, 1));
            params.put("pageSize", boundedInt(arguments.get("pageSize"), 1, 10, 5));
            // 硬条件覆盖：模型不能改城市/学历
            params.put("city", constraints.city());
            params.put("educationReq", constraints.educationReq());
        } else if (RECOMMEND_TOOL.equals(javaToolName)) {
            params.put("keyword", text(arguments.get("keyword")));
            params.put("city", constraints.city());
            params.put("limit", boundedInt(arguments.get("limit"), 1, 10, 5));
        } else {
            throw new BizException("工具不在岗位搜索白名单：" + javaToolName);
        }

        /*
         * R1 身份桥接（关键）：
         * 新入口在旧链路设置 AgentRuntimeContext 之前就接管，真实工具（JobSearchTool 等）
         * 读取 getRequiredUserId 会失败。这里在**工具执行边界**用服务端可信身份设置上下文，
         * 执行完 finally 恢复原上下文（可能是空）——绝不允许模型提供 userId。
         */
        AgentRuntimeContext.Context previous = AgentRuntimeContext.get();
        AgentRuntimeContext.set(
                input.userId(),
                input.conversationId(),
                input.traceId(),
                StringUtils.hasText(input.intentCode()) ? input.intentCode() : "JOB_SEARCH"
        );
        AgentToolExecutionResult toolResult;
        try {
            toolResult = agentToolInvoker.invoke(javaToolName, params, constraints.keyword());
        } finally {
            AgentRuntimeContext.clear();
            if (previous != null) {
                AgentRuntimeContext.restore(previous);
            }
        }

        if (!Boolean.TRUE.equals(toolResult.getSuccess())) {
            throw new BizException("工具执行失败：" + toolResult.getMessage());
        }

        String dataJson = toolResult.getDataJson();
        JobCandidates batch = SEARCH_TOOL.equals(javaToolName)
                ? candidatesAdapter.fromSearchResult(dataJson)
                : candidatesAdapter.fromRecommendResult(dataJson, null, null);
        /*
         * R3-6：Observation 返回统一结构（JobCandidates），避免“内部标准化、模型看原始结构”。
         * 同时保留真实字段供内部收集。
         */
        batch.items().forEach(item -> pool.putIfAbsent(item.jobId(), item));
        anyToolSucceeded[0] = true;
        return toJsonQuietly(batch);
    }

    /**
     * 节点 3+4：结果验真与对外响应。
     */
    private WorkflowOutcome validateAndRespond(AgentResult result,
                                               Map<Long, JobCandidates.JobCandidateItem> pool,
                                               boolean anyToolSucceeded,
                                               AgentRunContext context,
                                               SearchConstraints constraints) {
        List<AiChatMessage> messages = new ArrayList<>(context.messages());

        if (result.outcome() == AgentResult.Outcome.FAILED) {
            return failure(result.reasonCode(), result.summary(), List.of(), null,
                    messages, context.toolCalls(), context.turn());
        }

        if (result.outcome() == AgentResult.Outcome.NEED_INPUT) {
            return new WorkflowOutcome(AgentResult.Outcome.NEED_INPUT, null,
                    result.question(), List.of(), result.missingFields(), result.question(),
                    messages, context.toolCalls(), context.turn());
        }

        // 合格候选池（当前有效条件）：EMPTY 与 FOUND 都以它为准
        List<JobCandidates.JobCandidateItem> qualified = pool.values().stream()
                .filter(constraints::accepts)
                .toList();

        if (result.outcome() == AgentResult.Outcome.EMPTY) {
            // R4-1：EMPTY 必须“查询成功且合格候选池为空”
            if (!anyToolSucceeded) {
                return failure("EMPTY_WITHOUT_SEARCH", "未实际完成任何查询，不能声明没有岗位。",
                        List.of(), null, messages, context.toolCalls(), context.turn());
            }
            if (!qualified.isEmpty()) {
                return failure("EMPTY_WITH_QUALIFIED_CANDIDATES",
                        "已查询到 " + qualified.size() + " 个符合条件的岗位，不能声明没有结果。",
                        List.of(), null, messages, context.toolCalls(), context.turn());
            }
            return new WorkflowOutcome(AgentResult.Outcome.EMPTY, null,
                    "我按你的条件查询了岗位，但没有找到符合条件的候选。可以尝试放宽城市或关键词，要我调整后再查一次吗？",
                    List.of(), List.of(), null, messages, context.toolCalls(), context.turn());
        }

        // R4-2：FOUND 全量验真——全部 ID 必须真实、无重复、满足硬条件
        List<Long> ids = result.candidateIds();
        if (ids == null || ids.isEmpty()) {
            return failure("PROPOSAL_VERIFICATION_FAILED", "FOUND 未提交候选。",
                    List.of(), null, messages, context.toolCalls(), context.turn());
        }
        Set<Long> unique = new HashSet<>();
        for (Long jobId : ids) {
            if (jobId == null || !unique.add(jobId)) {
                return failure("PROPOSAL_VERIFICATION_FAILED", "候选列表包含空值或重复 ID，已拒绝。",
                        List.of(), null, messages, context.toolCalls(), context.turn());
            }
        }
        List<JobCandidates.JobCandidateItem> verified = new ArrayList<>();
        for (Long jobId : ids) {
            JobCandidates.JobCandidateItem item = pool.get(jobId);
            if (item == null || !constraints.accepts(item)) {
                return failure("PROPOSAL_VERIFICATION_FAILED",
                        "候选 " + jobId + " 与真实查询结果不一致或均不满足硬性条件，已拒绝。",
                        List.of(), null, messages, context.toolCalls(), context.turn());
            }
            verified.add(item);
        }

        // R4-4：只用服务端可信字段重建答复，不追加模型 summary
        return new WorkflowOutcome(AgentResult.Outcome.FOUND, null, buildAnswer(verified),
                verified, List.of(), null, messages, context.toolCalls(), context.turn());
    }

    /**
     * 对外答复：只用验证过的候选真实字段。
     * R7：薪资元/月 → 展示 K/月；公司缺失显示“公司未知”。
     */
    private String buildAnswer(List<JobCandidates.JobCandidateItem> verified) {
        StringBuilder builder = new StringBuilder();
        builder.append("按你的条件找到 ").append(verified.size()).append(" 个岗位：\n");
        int index = 1;
        for (JobCandidates.JobCandidateItem item : verified.stream().limit(MAX_CANDIDATES_IN_ANSWER).toList()) {
            Map<String, Object> raw = item.raw();
            String company = text(raw.get("companyName"));
            builder.append(index++).append(". ")
                    .append(text(raw.get("jobTitle"))).append("｜")
                    .append(StringUtils.hasText(company) ? company : "公司未知").append("｜")
                    .append(text(raw.get("city")));
            String salary = formatSalary(raw.get("minSalary"), raw.get("maxSalary"));
            if (StringUtils.hasText(salary)) {
                builder.append("｜").append(salary);
            }
            builder.append("\n");
        }
        if (verified.size() > MAX_CANDIDATES_IN_ANSWER) {
            builder.append("（还有 ").append(verified.size() - MAX_CANDIDATES_IN_ANSWER).append(" 个见岗位列表）\n");
        }
        return builder.toString().trim();
    }

    // 薪资展示：元/月 → K/月（20000-30000 → 20-30K/月）
    private String formatSalary(Object minValue, Object maxValue) {
        Integer min = number(minValue);
        Integer max = number(maxValue);
        if (min == null && max == null) {
            return null;
        }
        String left = min == null ? "?" : String.valueOf(Math.round(min / 1000.0));
        String right = max == null ? "?" : String.valueOf(Math.round(max / 1000.0));
        return left + "-" + right + "K/月";
    }

    private WorkflowOutcome failure(String reasonCode, String message, List<String> missingFields,
                                    String question, List<AiChatMessage> messages,
                                    int toolCalls, int turns) {
        return new WorkflowOutcome(AgentResult.Outcome.FAILED, reasonCode, message,
                List.of(), missingFields, question, messages, toolCalls, turns);
    }

    private AgentDefinition definition(SearchConstraints constraints) {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put(SEARCH_TOOL_ALIAS, SEARCH_TOOL);
        aliases.put(RECOMMEND_TOOL_ALIAS, RECOMMEND_TOOL);

        return new AgentDefinition(
                AGENT_NAME,
                systemPrompt(constraints),
                aliases,
                List.of(searchToolSpec(constraints), recommendToolSpec(constraints), terminalToolContract.spec()),
                TerminalToolContract.TOOL_NAME,
                AgentDefinition.DEFAULT_MAX_TURNS,
                AgentDefinition.DEFAULT_MAX_TOOL_CALLS,
                AgentDefinition.DEFAULT_DEADLINE_SECONDS
        );
    }

    private AiToolSpec searchToolSpec(SearchConstraints constraints) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "召回关键词，可换等价词，但不能改变目标方向"));
        properties.put("page", Map.of("type", "integer", "description", "页码，从 1 开始，最大 20"));
        properties.put("pageSize", Map.of("type", "integer", "description", "每页条数，1-10"));
        return new AiToolSpec(SEARCH_TOOL_ALIAS,
                "按条件搜索本地岗位库。目标方向与硬条件由系统锁定为：" + describeConstraints(constraints)
                        + "；你只能调整召回关键词和页码。",
                Map.of("type", "object", "properties", properties));
    }

    private AiToolSpec recommendToolSpec(SearchConstraints constraints) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "召回关键词"));
        properties.put("limit", Map.of("type", "integer", "description", "数量上限，1-10"));
        return new AiToolSpec(RECOMMEND_TOOL_ALIAS,
                "根据用户偏好推荐岗位。目标方向与硬条件由系统锁定为：" + describeConstraints(constraints) + "。",
                Map.of("type", "object", "properties", properties));
    }

    private String describeConstraints(SearchConstraints constraints) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(constraints.targetRole())) {
            parts.add("目标方向=" + constraints.targetRole());
        }
        if (StringUtils.hasText(constraints.city())) {
            parts.add("城市=" + constraints.city());
        }
        if (constraints.minSalary() != null) {
            parts.add("薪资>=" + Math.round(constraints.minSalary() / 1000.0) + "K/月");
        }
        if (StringUtils.hasText(constraints.educationReq())) {
            parts.add("学历=" + constraints.educationReq());
        }
        return parts.isEmpty() ? "无" : String.join("，", parts);
    }

    private String systemPrompt(SearchConstraints constraints) {
        return """
                你是求职平台的岗位搜索助手。你的任务是用工具找到符合用户条件的岗位，并在最后用 finish_job_search 提交结果。
                规则：
                1. 只能使用 job_search / job_recommend 两个工具。目标方向、城市、薪资、学历是硬条件，不能修改；
                   你可以换等价召回关键词，但不能改变目标方向（用户要 Java，就不能只查 Python）。
                2. 可以翻页补查；不要在相同条件下重复相同查询。
                3. 结果不足时如实说明实际数量，不要编造；一条都没有才提交 EMPTY，并说明查了什么。
                4. 只有确实需要用户补充信息时才提交 NEED_INPUT，给出具体问题和缺失字段。
                5. finish_job_search 必须单独一轮提交，不能和业务查询同轮；candidateIds 只能填工具真实返回过的 jobId。
                6. 你只能搜索和展示，不能代替用户投递或联系公司；不要在 summary 里描述未执行的动作。
                当前任务约束：%s
                """.formatted(describeConstraints(constraints));
    }

    private String toJsonQuietly(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"items\":[]}";
        }
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return text.length() > 60 ? text.substring(0, 60) : text;
    }

    private Integer number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int boundedInt(Object value, int min, int max, int fallback) {
        Integer parsed = null;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                parsed = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                parsed = null;
            }
        }
        if (parsed == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, parsed));
    }
}
