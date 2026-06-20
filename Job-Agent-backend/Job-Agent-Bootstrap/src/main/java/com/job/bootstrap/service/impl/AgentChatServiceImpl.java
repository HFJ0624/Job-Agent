package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.agent.JobAgentSummaryAssistant;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.intent.AgentIntentCode;
import com.job.bootstrap.agent.intent.AgentIntentRouter;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.AgentChatService;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.bootstrap.service.AgentPlanExecutorService;
import com.job.bootstrap.service.AgentPlanningService;
import com.job.bootstrap.service.AgentTraceService;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import com.job.common.vo.agent.AgentChatVO;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.common.vo.agent.AgentPlanStepVO;
import com.job.common.vo.agent.AgentPlanVO;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.enums.AgentPlanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 作者:hfj
 * 功能:AI 助手聊天服务实现
 * 职责：
 * 1. 获取或创建 AI 会话。
 * 2. 保存用户消息。
 * 3. 设置 AgentRuntimeContext。
 * 4. 调用 LangChain4j Agent。
 * 5. 保存助手回复。
 * 6. 保存主链路 Trace。
 * 日期: 2026/6/8 15:20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private static final int NOT_DELETED = 0;
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final int PRE_RETRIEVAL_LIMIT = 5;
    private static final int MEMORY_RETRIEVAL_LIMIT = 5;
    private static final int RAG_CONTENT_PREVIEW_LENGTH = 500;
    private static final String RAG_PRE_RETRIEVAL_TOOL_NAME = "RagPreRetrieval.searchKnowledge";

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;

    /**
     * 不带工具能力的总结助手。
     * Executor 执行完后，由它负责把工具结果整理成用户可读中文。
     */
    private final JobAgentSummaryAssistant jobAgentSummaryAssistant;

    /**
     * Agent Trace 统一记录服务。
     */
    private final AgentTraceService agentTraceService;

    /**
     * RAG 检索服务。
     * 用户每次询问 AI 助手时，都先用它从 pgvector 中召回相关知识。
     */
    private final RagRetrievalService ragRetrievalService;

    /**
     * 意图路由器。
     * 第一版使用规则识别，后续可以升级为大模型分类。
     */
    private final AgentIntentRouter agentIntentRouter;

    /**
     * Agent Planner 服务。
     */
    private final AgentPlanningService agentPlanningService;

    /**
     * Agent 计划执行器。
     */
    private final AgentPlanExecutorService agentPlanExecutorService;

    /**
     * Agent 长期记忆服务。
     * 本服务负责在执行总结前召回历史偏好、简历画像、面试反馈和历史决策。
     */
    private final AgentMemoryService agentMemoryService;

    /**
     * Agent 工具 Schema 注册中心。
     */
    private final AgentToolSchemaRegistry agentToolSchemaRegistry;

    /**
     * 统一 JSON 工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 执行一次 AI 对话。
     *
     * @param userId 当前登录用户ID
     * @param conversationId 会话ID，可以为空
     * @param planId 已存在的计划ID，可以为空
     * @param message 用户输入
     * @param confirmedToolNames 本轮用户已确认允许执行的工具名
     * @return Agent 回复
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentChatVO chat(
            Long userId,
            Long conversationId,
            Long planId,
            String message,
            List<String> confirmedToolNames
    ) {
        long start = System.currentTimeMillis();

        /*
         * 1. 每次用户发起一次对话，生成一个 traceId。
         *    这个 traceId 会贯穿:
         *    - 主对话日志
         *    - 工具调用日志
         *    - 异常日志
         */
        String traceId = UUID.randomUUID().toString().replace("-", "");

        /*
         * 2. 识别用户意图。
         *    当前只是规则识别，作用是:
         *    - Trace 日志可以分类
         *    - 后续可以根据意图走不同 Agent 编排流程
         */
        AgentIntentCode intentCode = agentIntentRouter.route(message);

        /*
         * 3. 如果前端传入 planId，说明这是“继续执行已有计划”。
         *    典型场景是上轮返回需要用户确认，用户确认后带 planId 和 confirmedToolNames 再次请求。
         *    这里先读取计划，是为了复用原计划的 conversationId，避免重新创建一条会话。
         */
        AgentPlanVO existingPlan = null;
        if (planId != null) {
            existingPlan = agentPlanningService.getUserPlan(userId, planId);
            if (conversationId == null) {
                conversationId = existingPlan.getConversationId();
            }
        }

        /*
         * 3. 获取或创建会话。
         *    conversationId 为空时自动创建新会话。
         */
        AiConversation conversation = getOrCreateConversation(userId, conversationId, message);

        /*
         * 4. 保存用户消息。
         */
        saveMessage(conversation.getId(), userId, ROLE_USER, message, null);

        AgentPlanVO plan = null;
        try {
            /*
             * 先确定本轮要执行的计划。
             * - 没有 planId：创建新计划。
             * - 有 planId：继续执行已有计划，不重新规划。
             */
            plan = existingPlan != null
                    ? existingPlan
                    : agentPlanningService.createPlan(
                            userId,
                            conversation.getId(),
                            traceId,
                            intentCode,
                            message
                    );
            String activeIntentCode = plan.getIntentCode();

            if (needClarification(plan)) {
                String answer = buildClarificationAnswer(plan);
                saveMessage(conversation.getId(), userId, ROLE_ASSISTANT, answer, null);
                touchConversation(conversation);

                agentTraceService.saveTrace(
                        traceId,
                        userId,
                        conversation.getId(),
                        activeIntentCode,
                        null,
                        buildPlanOnlyTraceInput(message, plan),
                        buildPlanOnlyTraceOutput(answer, plan),
                        "SUCCESS",
                        null,
                        System.currentTimeMillis() - start
                );

                AgentChatVO vo = new AgentChatVO();
                vo.setConversationId(conversation.getId());
                vo.setPlanId(plan.getId());
                vo.setAnswer(answer);
                vo.setRequiresUserConfirmation(false);
                vo.setRequiredConfirmationToolNames(List.of());
                return vo;
            }

            List<AgentToolSchema> unconfirmedTools = findUnconfirmedTools(plan, confirmedToolNames);
            if (!unconfirmedTools.isEmpty()) {
                String answer = buildConfirmationAnswer(unconfirmedTools);
                saveMessage(conversation.getId(), userId, ROLE_ASSISTANT, answer, null);
                touchConversation(conversation);

                agentTraceService.saveTrace(
                        traceId,
                        userId,
                        conversation.getId(),
                        activeIntentCode,
                        null,
                        buildPlanOnlyTraceInput(message, plan, confirmedToolNames),
                        buildPlanOnlyTraceOutput(answer, plan, unconfirmedTools),
                        "SUCCESS",
                        null,
                        System.currentTimeMillis() - start
                );

                AgentChatVO vo = new AgentChatVO();
                vo.setConversationId(conversation.getId());
                vo.setPlanId(plan.getId());
                vo.setAnswer(answer);
                vo.setRequiresUserConfirmation(true);
                vo.setRequiredConfirmationToolNames(toToolNames(unconfirmedTools));
                vo.setConfirmationMessage("请确认是否允许执行这些有副作用的工具。");
                return vo;
            }

            /*
             * 5. 设置 Agent 运行时上下文。
             *    重点:
             *    - userId 不让大模型传
             *    - conversationId 不让大模型传
             *    - traceId 不让大模型传
             *    - 工具内部通过 AgentRuntimeContext 获取
             */
            AgentRuntimeContext.set(
                    userId,
                    conversation.getId(),
                    traceId,
                    activeIntentCode,
                    confirmedToolNames
            );

            /*
             * 6. 执行计划。
             *
             * 关键变化:
             * - 之前是“模型看到计划后自己决定是否调用工具”。
             * - 现在是“后端 Executor 按 agent_plan_step 顺序确定性执行工具”。
             * - 每一步都会回写 step.status/resultSummary/errorMsg。
             */
            AgentPlanExecutionResult executionResult = agentPlanExecutorService.executePlan(userId, plan.getId());

            /*
             * 7.1 召回长期记忆。
             *
             * 设计说明:
             * 1. 这里放在 Executor 之后，是为了让本轮工具结果先完成沉淀，再统一召回历史和最新记忆。
             * 2. 召回结果只进入 Summary Assistant，不直接改写工具入参，避免破坏 Tool Schema 的第一版边界。
             * 3. 记忆库不可用时不影响主流程，retrieveLongTermMemories 内部会降级为空列表。
             */
            List<AgentMemoryVO> retrievedMemories = retrieveLongTermMemories(userId, message, plan);

            /*
             * 7. 总结执行结果。
             *
             * 注意:
             * 1. 这里使用不带 tools 的 Summary Assistant。
             * 2. 它只能总结 Executor 结果，不能再次调用工具。
             * 3. 这样可以避免已经执行过的工具被模型重复调用。
             */
            String answer = jobAgentSummaryAssistant.summarize(
                    buildExecutorSummaryMessage(message, plan, executionResult, retrievedMemories)
            );

            /*
             * 8. 保存助手消息。
             */
            saveMessage(conversation.getId(), userId, ROLE_ASSISTANT, answer, null);

            /*
             * 9. 更新会话时间。
             *    这样前端会话列表可以按最近聊天排序。
             */
            touchConversation(conversation);

            /*
             * 10. 保存主对话 Trace。
             *     工具调用 Trace 会在 Tool 内部单独保存，并且会带上 planId/stepId。
             *     主 Trace 记录计划执行汇总，方便从一条链路看到整体状态。
             */
            agentTraceService.saveTrace(
                    traceId,
                    userId,
                    conversation.getId(),
                    activeIntentCode,
                    null,
                    buildExecutorTraceInput(message, plan, confirmedToolNames, retrievedMemories),
                    buildExecutorTraceOutput(answer, plan, executionResult, retrievedMemories),
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            //返回结果
            AgentChatVO vo = new AgentChatVO();
            vo.setConversationId(conversation.getId());
            vo.setPlanId(plan.getId());
            vo.setAnswer(answer);
            vo.setRequiresUserConfirmation(false);
            vo.setRequiredConfirmationToolNames(List.of());
            return vo;

        } catch (Exception e) {
            /*
             * 11. 异常也必须落 Trace。
             *     企业级 Agent 项目中，失败链路比成功链路更重要。
             */
            agentTraceService.saveTrace(
                    traceId,
                    userId,
                    conversation.getId(),
                    plan == null ? intentCode.name() : plan.getIntentCode(),
                    null,
                    buildPlanOnlyTraceInput(message, plan),
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            throw e;

        } finally {
            /*
             * 12. 清理 ThreadLocal。
             *     这是必须做的，否则线程池复用时可能串用户。
             */
            AgentRuntimeContext.clear();
        }
    }

    /**
     * 前置检索 RAG 知识。
     *
     * 设计说明:
     * 1. 这里不依赖大模型是否“愿意”调用工具，而是后端强制先检索。
     * 2. 检索结果会进入本轮提示词，帮助模型基于真实简历、岗位、公司和沟通记录回答。
     * 3. 检索结果同时写入 Agent Trace，后台管理员可以看到回答依据。
     */
    private RagContext retrieveRagContext(Long userId, Long conversationId, String intentCode, String message) {
        long start = System.currentTimeMillis();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", message);
        input.put("limit", PRE_RETRIEVAL_LIMIT);
        input.put("stage", "BEFORE_AGENT_ANSWER");

        try {
            List<RagSearchResultVO> results = ragRetrievalService.search(userId, message, PRE_RETRIEVAL_LIMIT);
            List<Map<String, Object>> references = buildRagReferences(results, true);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("ragEnabled", true);
            output.put("ragStatus", "SUCCESS");
            output.put("hitCount", results.size());
            output.put("references", references);

            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    RAG_PRE_RETRIEVAL_TOOL_NAME,
                    input,
                    output,
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            return new RagContext(
                    true,
                    "SUCCESS",
                    null,
                    results,
                    buildRagEnhancedMessage(message, results, null)
            );
        } catch (Exception exception) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("ragEnabled", true);
            output.put("ragStatus", "FAILED");
            output.put("hitCount", 0);

            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    RAG_PRE_RETRIEVAL_TOOL_NAME,
                    input,
                    output,
                    "FAILED",
                    exception.getMessage(),
                    System.currentTimeMillis() - start
            );

            /*
             * RAG 失败时不直接中断用户聊天。
             * 原因:
             * - Embedding 服务、pgvector、网络都可能临时异常。
             * - 用户端 AI 助手仍可以给出通用回答。
             * - 后台 Trace 会明确标记 ragStatus=FAILED，便于管理员排查。
             */
            return new RagContext(
                    true,
                    "FAILED",
                    exception.getMessage(),
                    List.of(),
                    buildRagEnhancedMessage(message, List.of(), exception.getMessage())
            );
        }
    }

    /**
     * 构造带 RAG 知识的用户消息。
     *
     * 注意:
     * 1. 这里传给大模型，不直接保存到 ai_message 表。
     * 2. ai_message 表保存的仍然是用户原始输入，避免前端历史记录出现内部检索上下文。
     * 3. RAG 来源进入 Trace，而不是展示给普通用户。
     */
    private String buildRagEnhancedMessage(String originalMessage, List<RagSearchResultVO> results, String ragError) {
        StringBuilder builder = new StringBuilder();
        builder.append("【用户原始问题】\n")
                .append(originalMessage)
                .append("\n\n");

        builder.append("【系统已检索的 RAG 知识】\n");
        if (StringUtils.hasText(ragError)) {
            builder.append("本轮知识库检索失败，错误信息: ")
                    .append(ragError)
                    .append("\n");
            builder.append("如果继续回答，只能基于通用求职知识，不要声称已经读取到用户简历、岗位、公司或沟通记录。\n\n");
        } else if (CollectionUtils.isEmpty(results)) {
            builder.append("未检索到高相关知识片段。\n");
            builder.append("如果问题依赖用户简历、岗位、公司或沟通记录，请明确说明知识库里暂未找到依据，并给出下一步建议。\n\n");
        } else {
            for (int i = 0; i < results.size(); i++) {
                RagSearchResultVO result = results.get(i);
                builder.append("【知识片段 ")
                        .append(i + 1)
                        .append("】\n");
                builder.append("来源类型: ").append(nullToDash(result.getDocumentType())).append("\n");
                builder.append("标题: ").append(nullToDash(result.getTitle())).append("\n");
                builder.append("业务ID: ").append(result.getBusinessId()).append("\n");
                builder.append("分片序号: ").append(result.getChunkIndex()).append("\n");
                builder.append("相似度: ").append(formatScore(result.getScore())).append("\n");
                builder.append("内容:\n")
                        .append(result.getContent())
                        .append("\n\n");
            }
        }

        builder.append("【回答要求】\n");
        builder.append("1. 优先依据 RAG 知识回答用户问题，不能编造知识片段中不存在的事实。\n");
        builder.append("2. 普通用户前端不要展示向量ID、chunkIndex、score、metadata 等内部字段。\n");
        builder.append("3. 如果 RAG 没有命中或命中不足，要说明“知识库里暂未找到足够依据”，再给通用建议。\n");
        builder.append("4. 回答要自然、清晰、中文分点，像一个求职 Agent 助手，而不是把原始 JSON 贴给用户。\n");
        return builder.toString();
    }

    private boolean needClarification(AgentPlanVO plan) {
        return plan != null && AgentPlanStatus.NEED_CLARIFICATION.name().equals(plan.getStatus());
    }

    private String buildClarificationAnswer(AgentPlanVO plan) {
        List<String> missingParams = readStringList(plan.getMissingParamsJson());
        StringBuilder builder = new StringBuilder();
        builder.append("我已经把你的目标拆成了计划，但现在还缺少必要信息，先不直接调用工具。\n\n");
        builder.append("需要补充：\n");

        for (String param : missingParams) {
            builder.append("- ").append(paramLabel(param)).append("\n");
        }

        builder.append("\n请补充以上信息后，我再继续执行。");
        return builder.toString();
    }

    private List<AgentToolSchema> findUnconfirmedTools(AgentPlanVO plan, List<String> confirmedToolNames) {
        if (plan == null || CollectionUtils.isEmpty(plan.getSteps())) {
            return List.of();
        }

        Map<String, AgentToolSchema> unconfirmedTools = new LinkedHashMap<>();
        for (AgentPlanStepVO step : plan.getSteps()) {
            for (AgentToolSchema schema : agentToolSchemaRegistry.findByToolExpression(step.getToolName())) {
                if (Boolean.TRUE.equals(schema.getRequiresUserConfirmation())
                        && !isConfirmed(schema.getToolName(), confirmedToolNames)) {
                    unconfirmedTools.put(schema.getToolName(), schema);
                }
            }
        }
        return List.copyOf(unconfirmedTools.values());
    }

    private boolean isConfirmed(String toolName, List<String> confirmedToolNames) {
        if (!StringUtils.hasText(toolName) || CollectionUtils.isEmpty(confirmedToolNames)) {
            return false;
        }

        return confirmedToolNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(toolName::equals);
    }

    private String buildConfirmationAnswer(List<AgentToolSchema> unconfirmedTools) {
        StringBuilder builder = new StringBuilder();
        builder.append("我已经把你的目标拆成了计划，但里面包含需要你确认后才能执行的操作。\n\n");
        builder.append("需要确认的工具：\n");

        for (AgentToolSchema schema : unconfirmedTools) {
            builder.append("- ")
                    .append(schema.getDisplayName())
                    .append("（")
                    .append(schema.getToolName())
                    .append("）：")
                    .append(schema.getConfirmationMessage())
                    .append("\n");
        }

        builder.append("\n确认后请重新发送请求，并在 confirmedToolNames 中带上对应工具名。");
        return builder.toString();
    }

    private List<String> toToolNames(List<AgentToolSchema> schemas) {
        if (CollectionUtils.isEmpty(schemas)) {
            return List.of();
        }
        return schemas.stream().map(AgentToolSchema::getToolName).toList();
    }

    /**
     * 召回用户长期记忆。
     *
     * 方法步骤:
     * 1. 用用户原始输入、计划目标、意图和已抽取参数拼出检索词。
     * 2. 调用 AgentMemoryService 做结构化关键词检索。
     * 3. 如果记忆表未创建或查询失败，返回空列表，不影响本轮 Agent 回复。
     *
     * 注意:
     * 第一版只把长期记忆注入 Summary Assistant。
     * 后续如果要让记忆影响工具入参，可以在 Executor 的参数合并阶段读取固定 memoryKey。
     */
    private List<AgentMemoryVO> retrieveLongTermMemories(Long userId, String message, AgentPlanVO plan) {
        try {
            return agentMemoryService.searchMemories(
                    userId,
                    buildMemoryQuery(message, plan),
                    MEMORY_RETRIEVAL_LIMIT
            );
        } catch (Exception exception) {
            log.warn(
                    "Agent 长期记忆召回失败，userId={}, planId={}, error={}",
                    userId,
                    plan == null ? null : plan.getId(),
                    exception.getMessage(),
                    exception
            );
            return List.of();
        }
    }

    private String buildMemoryQuery(String message, AgentPlanVO plan) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(message)) {
            builder.append(message).append(' ');
        }
        if (plan != null) {
            appendIfText(builder, plan.getUserGoal());
            appendIfText(builder, plan.getIntentCode());
            appendIfText(builder, plan.getPlanTitle());
            appendIfText(builder, plan.getPlanSummary());
            appendIfText(builder, plan.getExtractedParamsJson());
        }
        return builder.toString().trim();
    }

    private void appendIfText(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(value).append(' ');
        }
    }

    private String buildExecutorSummaryMessage(
            String originalMessage,
            AgentPlanVO plan,
            AgentPlanExecutionResult executionResult,
            List<AgentMemoryVO> retrievedMemories
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("【当前用户输入】\n")
                .append(originalMessage)
                .append("\n\n");

        builder.append("【已召回的长期记忆】\n");
        if (CollectionUtils.isEmpty(retrievedMemories)) {
            builder.append("本轮没有召回到可用长期记忆。\n\n");
        } else {
            for (int i = 0; i < retrievedMemories.size(); i++) {
                AgentMemoryVO memory = retrievedMemories.get(i);
                builder.append(i + 1)
                        .append(". 类型: ")
                        .append(nullToDash(memory.getMemoryType()))
                        .append("，键: ")
                        .append(nullToDash(memory.getMemoryKey()))
                        .append("，摘要: ")
                        .append(nullToDash(memory.getSummary()))
                        .append("\n内容: ")
                        .append(preview(memory.getMemoryValue()))
                        .append("\n");
            }
            builder.append("\n");
        }

        builder.append("【计划原始目标】\n")
                .append(plan.getUserGoal())
                .append("\n\n");

        builder.append("【后端执行计划】\n");
        builder.append("计划ID: ").append(plan.getId()).append("\n");
        builder.append("计划标题: ").append(nullToDash(plan.getPlanTitle())).append("\n");
        builder.append("计划摘要: ").append(nullToDash(plan.getPlanSummary())).append("\n");
        builder.append("意图: ").append(nullToDash(plan.getIntentCode())).append("\n\n");

        builder.append("【Executor 执行结果】\n");
        builder.append(toJson(executionResult)).append("\n\n");

        builder.append("【总结要求】\n");
        builder.append("1. 只总结 Executor 已经完成的步骤和工具结果。\n");
        builder.append("2. 不要再次调用工具，不要说“我将要调用”。\n");
        builder.append("3. 如果执行成功，直接给用户可读结论和下一步建议。\n");
        builder.append("4. 如果执行失败，说明失败步骤、原因和用户可以补充什么。\n");
        return builder.toString();
    }

    private Map<String, Object> buildExecutorTraceInput(
            String message,
            AgentPlanVO plan,
            List<String> confirmedToolNames,
            List<AgentMemoryVO> retrievedMemories
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", message);
        input.put("confirmedToolNames", confirmedToolNames == null ? List.of() : confirmedToolNames);
        input.put("agentPlan", buildPlanSnapshot(plan));
        input.put("memoryHitCount", retrievedMemories == null ? 0 : retrievedMemories.size());
        input.put("retrievedMemories", buildMemorySnapshots(retrievedMemories));
        input.put("executionMode", "PLAN_EXECUTOR");
        return input;
    }

    private Map<String, Object> buildExecutorTraceOutput(
            String answer,
            AgentPlanVO plan,
            AgentPlanExecutionResult executionResult,
            List<AgentMemoryVO> retrievedMemories
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", answer);
        output.put("planId", plan == null ? null : plan.getId());
        output.put("executionResult", executionResult);
        output.put("memoryHitCount", retrievedMemories == null ? 0 : retrievedMemories.size());
        output.put("retrievedMemories", buildMemorySnapshots(retrievedMemories));
        output.put("executionMode", "PLAN_EXECUTOR");
        return output;
    }

    private List<Map<String, Object>> buildMemorySnapshots(List<AgentMemoryVO> memories) {
        if (CollectionUtils.isEmpty(memories)) {
            return List.of();
        }

        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (AgentMemoryVO memory : memories) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", memory.getId());
            snapshot.put("userId", memory.getUserId());
            snapshot.put("memoryType", memory.getMemoryType());
            snapshot.put("memoryKey", memory.getMemoryKey());
            snapshot.put("summary", memory.getSummary());
            snapshot.put("sourceType", memory.getSourceType());
            snapshot.put("sourceId", memory.getSourceId());
            snapshot.put("confidence", memory.getConfidence());
            snapshot.put("importance", memory.getImportance());
            snapshot.put("lastUsedTime", memory.getLastUsedTime());
            snapshot.put("contentPreview", preview(memory.getMemoryValue()));
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String paramLabel(String param) {
        return switch (param) {
            case "resumeId" -> "简历ID（resumeId）";
            case "jobId" -> "岗位ID（jobId）";
            case "applicationId" -> "投递记录ID（applicationId）";
            case "mockSessionId" -> "模拟面试会话ID（mockSessionId）";
            default -> param;
        };
    }

    private String buildPlannedAgentMessage(String enhancedMessage, AgentPlanVO plan) {
        if (plan == null) {
            return enhancedMessage;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("【后端生成的执行计划】\n");
        builder.append("计划ID: ").append(plan.getId()).append("\n");
        builder.append("计划标题: ").append(nullToDash(plan.getPlanTitle())).append("\n");
        builder.append("计划摘要: ").append(nullToDash(plan.getPlanSummary())).append("\n");
        builder.append("意图: ").append(nullToDash(plan.getIntentCode())).append("\n");
        builder.append("状态: ").append(nullToDash(plan.getStatus())).append("\n");
        builder.append("已抽取参数: ").append(nullToDash(plan.getExtractedParamsJson())).append("\n");
        builder.append("缺失参数: ").append(nullToDash(plan.getMissingParamsJson())).append("\n");
        builder.append("步骤:\n");

        if (CollectionUtils.isEmpty(plan.getSteps())) {
            builder.append("- 暂无步骤\n");
        } else {
            for (AgentPlanStepVO step : plan.getSteps()) {
                builder.append(step.getStepNo()).append(". ")
                        .append(nullToDash(step.getStepName()))
                        .append("，目标: ")
                        .append(nullToDash(step.getStepGoal()))
                        .append("，建议工具: ")
                        .append(nullToDash(step.getToolName()))
                        .append("，完成条件: ")
                        .append(nullToDash(step.getCompletionCriteria()))
                        .append("\n");
            }
        }

        builder.append("\n【执行要求】\n");
        builder.append("1. 必须优先按后端计划选择工具。\n");
        builder.append("2. 缺参时不能编造参数，也不能跳过计划要求的关键参数。\n");
        builder.append("3. 工具返回 JSON 后整理成中文，不要把原始 JSON 直接贴给用户。\n\n");
        builder.append(enhancedMessage);
        return builder.toString();
    }

    private Map<String, Object> buildPlanOnlyTraceInput(String message, AgentPlanVO plan) {
        return buildPlanOnlyTraceInput(message, plan, List.of());
    }

    private Map<String, Object> buildPlanOnlyTraceInput(
            String message,
            AgentPlanVO plan,
            List<String> confirmedToolNames
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", message);
        input.put("confirmedToolNames", confirmedToolNames == null ? List.of() : confirmedToolNames);
        input.put("agentPlan", buildPlanSnapshot(plan));
        return input;
    }

    private Map<String, Object> buildPlanOnlyTraceOutput(String answer, AgentPlanVO plan) {
        return buildPlanOnlyTraceOutput(answer, plan, List.of());
    }

    private Map<String, Object> buildPlanOnlyTraceOutput(
            String answer,
            AgentPlanVO plan,
            List<AgentToolSchema> unconfirmedTools
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", answer);
        output.put("agentPlan", buildPlanSnapshot(plan));
        output.put("unconfirmedTools", buildToolSchemaSnapshots(unconfirmedTools));
        return output;
    }

    private List<Map<String, Object>> buildToolSchemaSnapshots(List<AgentToolSchema> schemas) {
        if (CollectionUtils.isEmpty(schemas)) {
            return List.of();
        }

        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (AgentToolSchema schema : schemas) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("toolName", schema.getToolName());
            snapshot.put("displayName", schema.getDisplayName());
            snapshot.put("permissionType", schema.getPermissionType());
            snapshot.put("sideEffectType", schema.getSideEffectType());
            snapshot.put("hasSideEffect", schema.getHasSideEffect());
            snapshot.put("requiresUserConfirmation", schema.getRequiresUserConfirmation());
            snapshot.put("confirmationMessage", schema.getConfirmationMessage());
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private Map<String, Object> buildPlanSnapshot(AgentPlanVO plan) {
        if (plan == null) {
            return Map.of();
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", plan.getId());
        snapshot.put("traceId", plan.getTraceId());
        snapshot.put("userId", plan.getUserId());
        snapshot.put("conversationId", plan.getConversationId());
        snapshot.put("intentCode", plan.getIntentCode());
        snapshot.put("planTitle", plan.getPlanTitle());
        snapshot.put("planSummary", plan.getPlanSummary());
        snapshot.put("requiredParamsJson", plan.getRequiredParamsJson());
        snapshot.put("extractedParamsJson", plan.getExtractedParamsJson());
        snapshot.put("missingParamsJson", plan.getMissingParamsJson());
        snapshot.put("status", plan.getStatus());

        List<Map<String, Object>> steps = new ArrayList<>();
        if (!CollectionUtils.isEmpty(plan.getSteps())) {
            for (AgentPlanStepVO step : plan.getSteps()) {
                Map<String, Object> stepSnapshot = new LinkedHashMap<>();
                stepSnapshot.put("id", step.getId());
                stepSnapshot.put("stepNo", step.getStepNo());
                stepSnapshot.put("stepName", step.getStepName());
                stepSnapshot.put("stepGoal", step.getStepGoal());
                stepSnapshot.put("toolName", step.getToolName());
                stepSnapshot.put("toolInputSchema", step.getToolInputSchema());
                stepSnapshot.put("completionCriteria", step.getCompletionCriteria());
                stepSnapshot.put("status", step.getStatus());
                steps.add(stepSnapshot);
            }
        }
        snapshot.put("steps", steps);
        return snapshot;
    }

    private Map<String, Object> buildChatTraceInput(String message, RagContext ragContext, AgentPlanVO plan) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", message);
        input.put("ragEnabled", ragContext.enabled());
        input.put("ragStatus", ragContext.status());
        input.put("ragHitCount", ragContext.results().size());
        input.put("agentPlan", buildPlanSnapshot(plan));
        return input;
    }

    private Map<String, Object> buildChatTraceOutput(String answer, RagContext ragContext, AgentPlanVO plan) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", answer);
        output.put("ragEnabled", ragContext.enabled());
        output.put("ragStatus", ragContext.status());
        output.put("ragError", ragContext.errorMessage());
        output.put("ragHitCount", ragContext.results().size());
        output.put("ragReferences", buildRagReferences(ragContext.results(), false));
        output.put("agentPlan", buildPlanSnapshot(plan));
        return output;
    }

    /**
     * 构造 RAG 来源引用。
     *
     * @param results RAG 检索结果
     * @param includeContent 是否保留完整分片内容
     * @return 可直接写入 Trace outputData 的来源列表
     */
    private List<Map<String, Object>> buildRagReferences(List<RagSearchResultVO> results, boolean includeContent) {
        if (CollectionUtils.isEmpty(results)) {
            return List.of();
        }

        List<Map<String, Object>> references = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RagSearchResultVO result = results.get(i);
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("rank", i + 1);
            reference.put("knowledgeId", result.getId());
            reference.put("owner", result.getUserId() != null && result.getUserId() == 0 ? "PUBLIC" : "PRIVATE");
            reference.put("userId", result.getUserId());
            reference.put("documentType", result.getDocumentType());
            reference.put("businessId", result.getBusinessId());
            reference.put("chunkIndex", result.getChunkIndex());
            reference.put("title", result.getTitle());
            reference.put("source", result.getSource());
            reference.put("score", result.getScore());
            reference.put("metadata", result.getMetadata());
            if (includeContent) {
                reference.put("content", result.getContent());
            } else {
                reference.put("contentPreview", preview(result.getContent()));
            }
            references.add(reference);
        }
        return references;
    }

    private String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content.trim();
        return text.length() <= RAG_CONTENT_PREVIEW_LENGTH
                ? text
                : text.substring(0, RAG_CONTENT_PREVIEW_LENGTH) + "...";
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "-";
        }
        return String.format("%.4f", score);
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    /**
     * 本轮聊天使用的 RAG 上下文。
     *
     * @param enabled 是否启用 RAG
     * @param status 检索状态
     * @param errorMessage 检索异常信息
     * @param results 召回结果
     * @param enhancedMessage 注入 RAG 知识后的模型输入
     */
    private record RagContext(
            boolean enabled,
            String status,
            String errorMessage,
            List<RagSearchResultVO> results,
            String enhancedMessage
    ) {
    }

    /**
     * 获取或创建会话。
     */
    private AiConversation getOrCreateConversation(Long userId, Long conversationId, String firstMessage) {
        if (conversationId != null) {
            AiConversation exist = aiConversationMapper.selectById(conversationId);

            /*
             * 只允许用户访问自己的会话。
             * 这一步是用户数据隔离，防止越权访问。
             */
            if (exist != null && userId.equals(exist.getUserId())) {
                return exist;
            }
        }

        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setConversationType("JOB_AGENT");
        conversation.setTitle(buildConversationTitle(firstMessage));
        conversation.setIsDeleted(NOT_DELETED);
        aiConversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 生成会话标题。
     */
    private String buildConversationTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新的求职对话";
        }

        String title = message.trim();
        return title.length() > 20 ? title.substring(0, 20) + "..." : title;
    }

    /**
     * 保存一条聊天消息。
     */
    private void saveMessage(Long conversationId, Long userId, String role, String content, String toolName) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setToolName(toolName);
        message.setTokenCount(0);
        message.setIsDeleted(NOT_DELETED);
        aiMessageMapper.insert(message);
    }

    /**
     * 更新会话更新时间。
     */
    private void touchConversation(AiConversation conversation) {
        conversation.setUpdateTime(new java.util.Date());
        aiConversationMapper.updateById(conversation);
    }
}
