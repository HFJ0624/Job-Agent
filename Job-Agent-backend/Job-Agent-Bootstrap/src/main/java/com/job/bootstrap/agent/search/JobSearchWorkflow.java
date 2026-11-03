package com.job.bootstrap.agent.search;

import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolInvoker;
import com.job.bootstrap.agent.react.AgentDefinition;
import com.job.bootstrap.agent.react.AgentResult;
import com.job.bootstrap.agent.react.AgentRunContext;
import com.job.bootstrap.agent.react.ReActRunner;
import com.job.bootstrap.agent.react.TerminalToolContract;
import com.job.bootstrap.ai.turn.AiToolSpec;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 岗位搜索 Workflow（第一阶段四节点）。
 *
 * 固定骨架（文档规定）: VALIDATE_INPUT → JOB_SEARCH_REACT → VALIDATE_RESULT → RESPOND。
 *
 * 职责边界:
 * 1. Workflow 管：条件快照与合并、工具白名单、结果验真、候选重建、对外响应；
 * 2. ReAct 管：在允许范围内选工具、调等价关键词/页码、决定继续或终结；
 * 3. 关键红线（文档）:
 *    - 城市/薪资/学历是硬条件，模型不能改——执行器用约束快照覆盖；
 *    - 终结提议的 candidateIds 必须来自真实工具结果，且满足硬条件，否则整单 FAILED；
 *    - EMPTY 必须“至少一次合法查询成功且无合格候选”才成立；
 *    - 对外成功答复只用验证过的候选，服务端重建内容，不采用模型转述。
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

    /** 每轮最多返回给模型的候选数量（上下文控制）。 */
    private static final int MAX_CANDIDATES_IN_ANSWER = 5;

    private final ReActRunner reActRunner;
    private final TerminalToolContract terminalToolContract;
    private final AgentToolInvoker agentToolInvoker;
    private final JobCandidatesAdapter candidatesAdapter;

    /**
     * Workflow 输入。
     *
     * @param userId         用户ID（可信身份，来自后端）
     * @param traceId        链路ID
     * @param userMessage    本轮用户输入
     * @param constraints    条件快照（已合并显式与偏好）
     * @param resumeMessages 续跑时的历史消息（null 表示新 run）
     * @param resumeTurn     续跑时的轮次
     * @param resumeToolCalls 续跑时的工具调用次数
     * @param startedAtMillis 续跑时的开始时间（预算延续）
     */
    public record WorkflowInput(
            Long userId,
            String traceId,
            String userMessage,
            SearchConstraints constraints,
            List<com.job.bootstrap.ai.turn.AiChatMessage> resumeMessages,
            int resumeTurn,
            int resumeToolCalls,
            long startedAtMillis
    ) {
    }

    /**
     * Workflow 输出。
     *
     * @param outcome       FOUND/EMPTY/NEED_INPUT/FAILED
     * @param reasonCode    运行时或业务原因码
     * @param answer        对外答复（只用验证过的候选重建）
     * @param candidates    验证过的候选（原始字段）
     * @param missingFields 缺失字段（NEED_INPUT）
     * @param question      向用户的问题（NEED_INPUT）
     * @param messages      本轮完整消息序列（持久化用）
     * @param toolCallsUsed 业务工具调用次数
     * @param turnsUsed     轮次
     */
    public record WorkflowOutcome(
            AgentResult.Outcome outcome,
            String reasonCode,
            String answer,
            List<JobCandidates.JobCandidateItem> candidates,
            List<String> missingFields,
            String question,
            List<com.job.bootstrap.ai.turn.AiChatMessage> messages,
            int toolCallsUsed,
            int turnsUsed
    ) {
    }

    /**
     * 执行四节点工作流。
     */
    public WorkflowOutcome execute(WorkflowInput input) {
        SearchConstraints constraints = input.constraints() == null
                ? new SearchConstraints(null, null, null, null, Map.of())
                : input.constraints();

        // 节点 1：输入校验（可选条件不硬性要求；只有空输入才拒绝）
        String userMessage = StringUtils.hasText(input.userMessage()) ? input.userMessage().trim() : "";
        if (!StringUtils.hasText(userMessage)) {
            return failure("NEED_INPUT", "输入为空，请描述要找的岗位（城市、方向可选）。",
                    List.of("query"), "请告诉我你要找的岗位方向或城市。", List.of(), 0, 0);
        }

        // 节点 2：ReAct（工具执行 + 实时收集真实候选）
        Map<Long, JobCandidates.JobCandidateItem> pool = new LinkedHashMap<>();
        boolean[] anyToolSucceeded = {false};
        AgentRunContext context = input.resumeMessages() == null || input.resumeMessages().isEmpty()
                ? new AgentRunContext(systemPrompt(constraints), userMessage)
                : new AgentRunContext(input.resumeMessages(), input.resumeTurn(), input.resumeToolCalls(),
                        input.startedAtMillis());
        if (input.resumeMessages() != null && !input.resumeMessages().isEmpty()) {
            // 续跑：追加本轮用户输入
            context.append(com.job.bootstrap.ai.turn.AiChatMessage.user(userMessage));
        }

        AgentResult result = reActRunner.run(
                definition(constraints),
                context,
                (javaToolName, arguments) -> executeTool(javaToolName, arguments, constraints, pool, anyToolSucceeded),
                input.userId(),
                input.traceId()
        );

        // 节点 3：结果验真
        return validateAndRespond(result, pool, anyToolSucceeded[0], context, constraints);
    }

    /**
     * 工具执行：硬条件覆盖 + 统一执行器（Guard/Schema/Trace）调用 + 真实结果收集。
     */
    private String executeTool(String javaToolName,
                               Map<String, Object> arguments,
                               SearchConstraints constraints,
                               Map<Long, JobCandidates.JobCandidateItem> pool,
                               boolean[] anyToolSucceeded) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (SEARCH_TOOL.equals(javaToolName)) {
            // 关键词/页码/页大小允许模型决定（策略空间），做长度与范围约束
            String keyword = text(arguments.get("keyword"));
            params.put("keyword", keyword);
            params.put("page", boundedInt(arguments.get("page"), 1, 20, 1));
            params.put("pageSize", boundedInt(arguments.get("pageSize"), 1, 10, 5));
            // 硬条件：用约束快照覆盖模型给的值（模型不能改城市/学历）
            params.put("city", constraints.city());
            params.put("educationReq", constraints.educationReq());
        } else if (RECOMMEND_TOOL.equals(javaToolName)) {
            params.put("keyword", text(arguments.get("keyword")));
            params.put("city", constraints.city());
            params.put("limit", boundedInt(arguments.get("limit"), 1, 10, 5));
        } else {
            // Runner 已做白名单校验，这里是纵深防御
            throw new BizException("工具不在岗位搜索白名单：" + javaToolName);
        }

        AgentToolExecutionResult toolResult = agentToolInvoker.invoke(javaToolName, params, constraints.keyword());
        if (!Boolean.TRUE.equals(toolResult.getSuccess())) {
            throw new BizException("工具执行失败：" + toolResult.getMessage());
        }

        anyToolSucceeded[0] = true;
        String dataJson = toolResult.getDataJson();
        JobCandidates batch = SEARCH_TOOL.equals(javaToolName)
                ? candidatesAdapter.fromSearchResult(dataJson)
                : candidatesAdapter.fromRecommendResult(dataJson, null, null);
        // 实时收集真实候选（去重），供结果验真使用
        batch.items().forEach(item -> pool.putIfAbsent(item.jobId(), item));
        return dataJson;
    }

    /**
     * 节点 3+4：验真并生成响应。
     */
    private WorkflowOutcome validateAndRespond(AgentResult result,
                                               Map<Long, JobCandidates.JobCandidateItem> pool,
                                               boolean anyToolSucceeded,
                                               AgentRunContext context,
                                               SearchConstraints constraints) {
        List<com.job.bootstrap.ai.turn.AiChatMessage> messages = new ArrayList<>(context.messages());

        if (result.outcome() == AgentResult.Outcome.FAILED) {
            return failure(result.reasonCode(), result.summary(), List.of(), null,
                    messages, context.toolCalls(), context.turn());
        }

        if (result.outcome() == AgentResult.Outcome.NEED_INPUT) {
            return new WorkflowOutcome(AgentResult.Outcome.NEED_INPUT, null,
                    result.question(), List.of(), result.missingFields(), result.question(),
                    messages, context.toolCalls(), context.turn());
        }

        if (result.outcome() == AgentResult.Outcome.EMPTY) {
            // 文档红线：EMPTY 必须“至少一次合法查询成功且无合格候选”才成立
            if (!anyToolSucceeded) {
                return failure("EMPTY_WITHOUT_SEARCH", "未实际完成任何查询，不能声明没有岗位。",
                        List.of(), null, messages, context.toolCalls(), context.turn());
            }
            return new WorkflowOutcome(AgentResult.Outcome.EMPTY, null,
                    "我按你的条件查询了岗位，但没有找到符合条件的候选。可以尝试放宽城市或关键词，要我调整后再查一次吗？",
                    List.of(), List.of(), null, messages, context.toolCalls(), context.turn());
        }

        // FOUND：候选必须来自真实结果且满足硬条件
        List<JobCandidates.JobCandidateItem> verified = new ArrayList<>();
        boolean proposalExists = false;
        for (Long jobId : result.candidateIds()) {
            if (jobId == null) {
                continue;
            }
            proposalExists = true;
            JobCandidates.JobCandidateItem item = pool.get(jobId);
            if (item != null && constraints.accepts(item)) {
                verified.add(item);
            }
        }
        if (!proposalExists || verified.isEmpty()) {
            return failure("PROPOSAL_VERIFICATION_FAILED",
                    "模型提交的候选与真实查询结果不一致或均不满足硬性条件，已拒绝。",
                    List.of(), null, messages, context.toolCalls(), context.turn());
        }

        // 服务端重建对外内容（只用真实字段，不采用模型转述）
        String answer = buildAnswer(verified, result.summary());
        return new WorkflowOutcome(AgentResult.Outcome.FOUND, null, answer, verified,
                List.of(), null, messages, context.toolCalls(), context.turn());
    }

    // 对外答复：只用验证过的候选重建（岗位名/公司/城市/薪资），模型最多贡献一句 summary
    private String buildAnswer(List<JobCandidates.JobCandidateItem> verified, String summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("按你的条件找到 ").append(verified.size()).append(" 个岗位：\n");
        int index = 1;
        for (JobCandidates.JobCandidateItem item : verified.stream().limit(MAX_CANDIDATES_IN_ANSWER).toList()) {
            Map<String, Object> raw = item.raw();
            builder.append(index++).append(". ")
                    .append(text(raw.get("jobTitle"))).append("｜")
                    .append(text(raw.get("companyName"))).append("｜")
                    .append(text(raw.get("city")));
            Integer min = number(raw.get("minSalary"));
            Integer max = number(raw.get("maxSalary"));
            if (min != null || max != null) {
                builder.append("｜").append(min == null ? "?" : min).append("-").append(max == null ? "?" : max).append("K");
            }
            builder.append("\n");
        }
        if (verified.size() > MAX_CANDIDATES_IN_ANSWER) {
            builder.append("（还有 ").append(verified.size() - MAX_CANDIDATES_IN_ANSWER).append(" 个见岗位列表）\n");
        }
        if (StringUtils.hasText(summary)) {
            builder.append("\n").append(summary.trim());
        }
        return builder.toString().trim();
    }

    private WorkflowOutcome failure(String reasonCode, String message, List<String> missingFields,
                                    String question, List<com.job.bootstrap.ai.turn.AiChatMessage> messages,
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
        properties.put("keyword", Map.of("type", "string", "description", "岗位关键词，可换等价词"));
        properties.put("page", Map.of("type", "integer", "description", "页码，从 1 开始，最大 20"));
        properties.put("pageSize", Map.of("type", "integer", "description", "每页条数，1-10"));
        return new AiToolSpec(SEARCH_TOOL_ALIAS,
                "按条件搜索本地岗位库。城市/学历等硬条件由系统锁定为：" + describeConstraints(constraints)
                        + "；你只能调整关键词和页码。",
                Map.of("type", "object", "properties", properties));
    }

    private AiToolSpec recommendToolSpec(SearchConstraints constraints) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "岗位关键词"));
        properties.put("limit", Map.of("type", "integer", "description", "数量上限，1-10"));
        return new AiToolSpec(RECOMMEND_TOOL_ALIAS,
                "根据用户偏好推荐岗位。城市等硬条件由系统锁定为：" + describeConstraints(constraints) + "。",
                Map.of("type", "object", "properties", properties));
    }

    private String describeConstraints(SearchConstraints constraints) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(constraints.city())) {
            parts.add("城市=" + constraints.city());
        }
        if (constraints.minSalary() != null) {
            parts.add("薪资>=" + constraints.minSalary() + "K");
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
                1. 只能使用 job_search / job_recommend 两个工具；城市、薪资、学历是硬条件，不能修改，也不要在参数里尝试改变。
                2. 你可以调整关键词的同义表述、翻页补查；不要在相同条件下重复相同查询。
                3. 如果结果不足，如实说明实际数量，不要编造岗位；如果一条都没有，提交 EMPTY 并说明查了什么。
                4. 只有确实需要用户补充信息时才提交 NEED_INPUT，并给出具体问题和缺失字段。
                5. finish_job_search 必须单独一轮提交，不能和业务查询同轮；candidateIds 只能填写工具真实返回过的 jobId。
                当前硬条件：%s
                """.formatted(describeConstraints(constraints));
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
