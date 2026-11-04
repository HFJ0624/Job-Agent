package com.job.bootstrap.agent.runtime.executor;

import com.job.bootstrap.agent.domain.tools.GreetingGenerateTool;
import com.job.bootstrap.agent.domain.tools.InterviewPrepareTool;
import com.job.bootstrap.agent.domain.tools.JobMatchTool;
import com.job.bootstrap.agent.domain.tools.JobRecommendTool;
import com.job.bootstrap.agent.domain.tools.JobSearchTool;
import com.job.bootstrap.agent.domain.tools.MockInterviewReviewTool;
import com.job.bootstrap.agent.domain.tools.RagSearchTool;
import com.job.bootstrap.agent.domain.tools.ResumeAnalyzeTool;
import com.job.bootstrap.agent.runtime.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.agent.support.guardrail.AgentGuardrailService;
import com.job.bootstrap.service.AdminExternalConnectorService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能: Agent 工具统一调用器（工具执行边界）。
 *
 * <p>职责固定为四步，顺序不可调整：</p>
 * <ol>
 *   <li>按工具名从**注册表**取执行器（模型不能绕过白名单）；</li>
 *   <li>执行工具（参数映射在注册时绑定，业务实现仍复用现有 Tool）；</li>
 *   <li>输出 JSON 校验（Schema）；</li>
 *   <li>统一包装结果（失败映射成错误码，不抛给上层）。</li>
 * </ol>
 *
 * <p>本轮重构：把原来的 9 分支 switch 改成启动时构建的 {@code Map<String, ToolCall>}，
 * 形成**唯一登记入口**；重名注册会在启动阶段直接失败，避免静默覆盖。
 * 新增工具只需要在 {@link #initHandlers()} 里加一行 register。</p>
 *
 * <p>注意：本类仍持有 8 个业务 Tool 的引用（参数映射需要），
 * 真正的"内核不认识业务"要等 Tool 侧改为实现统一 handler 接口后再做，
 * 届时本类只依赖注册表。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolInvoker {

    private final ResumeAnalyzeTool resumeAnalyzeTool;
    private final JobMatchTool jobMatchTool;
    private final GreetingGenerateTool greetingGenerateTool;
    private final JobSearchTool jobSearchTool;
    private final JobRecommendTool jobRecommendTool;
    private final InterviewPrepareTool interviewPrepareTool;
    private final MockInterviewReviewTool mockInterviewReviewTool;
    private final RagSearchTool ragSearchTool;
    private final AgentToolSchemaRegistry agentToolSchemaRegistry;
    private final AgentGuardrailService agentGuardrailService;
    private final AdminExternalConnectorService adminExternalConnectorService;

    /** 工具名 → 执行器；启动时一次性构建，运行期只读。 */
    private Map<String, ToolCall> handlers;

    /**
     * 工具执行器：参数映射在注册时确定，避免运行期再按名字散落分发。
     */
    @FunctionalInterface
    private interface ToolCall {
        String execute(Map<String, Object> params, String originalMessage) throws Exception;
    }

    /**
     * 注册全部工具（唯一登记入口）。
     *
     * <p>重名会抛 {@link IllegalStateException}，让配置错误在启动时暴露，而不是静默覆盖。</p>
     */
    @PostConstruct
    void initHandlers() {
        Map<String, ToolCall> map = new LinkedHashMap<>();

        register(map, "ResumeAnalyzeTool.analyzeResume", (p, m) -> resumeAnalyzeTool.analyzeResume(
                getLong(p, "resumeId"),
                getString(p, "resumeName"),
                getString(p, "targetPosition")
        ));
        register(map, "JobMatchTool.matchJob", (p, m) -> jobMatchTool.matchJob(
                getLong(p, "resumeId"),
                getString(p, "resumeName"),
                getLong(p, "jobId"),
                getString(p, "jobTitle"),
                // 任务 J5：本轮显式条件，参与硬性条件判定（优先于长期偏好）
                getString(p, "city"),
                getString(p, "keyword"),
                getInteger(p, "minSalary")
        ));
        register(map, "GreetingGenerateTool.generateGreeting", (p, m) -> greetingGenerateTool.generateGreeting(
                getLong(p, "resumeId"),
                getString(p, "resumeName"),
                getLong(p, "jobId"),
                getString(p, "jobTitle"),
                getString(p, "style")
        ));
        register(map, "JobSearchTool.searchJobs", (p, m) -> jobSearchTool.searchJobs(
                getString(p, "keyword"),
                getString(p, "city"),
                getString(p, "educationReq"),
                getString(p, "experienceReq"),
                getInteger(p, "page"),
                getInteger(p, "pageSize")
        ));
        register(map, "JobRecommendTool.recommendJobs", (p, m) -> jobRecommendTool.recommendJobs(
                getString(p, "keyword"),
                getString(p, "city"),
                getInteger(p, "limit")
        ));
        register(map, "InterviewPrepareTool.prepareInterview", (p, m) -> interviewPrepareTool.prepareInterview(
                getLong(p, "applicationId"),
                getLong(p, "resumeId")
        ));
        register(map, "MockInterviewReviewTool.generateMockInterviewReview",
                (p, m) -> mockInterviewReviewTool.generateMockInterviewReview(
                        firstLong(p, "sessionId", "mockSessionId")
                ));
        register(map, "RagSearchTool.searchKnowledge", (p, m) -> ragSearchTool.searchKnowledge(
                firstString(p, "query", "message", "keyword", m),
                getInteger(p, "limit")
        ));

        // 外部连接器：统一走 AdminExternalConnectorService，各自保留独立工具名
        for (String connectorTool : CONNECTOR_TOOLS) {
            register(map, connectorTool, (p, m) -> adminExternalConnectorService.invokeConnector(connectorTool, p));
        }

        this.handlers = Map.copyOf(map);
        log.info("Agent 工具注册完成，共 {} 个：{}", handlers.size(), handlers.keySet());
    }

    /** 外部连接器工具名（仍由 AdminExternalConnectorService 执行）。 */
    private static final java.util.List<String> CONNECTOR_TOOLS = java.util.List.of(
            "RecruitmentPlatformConnectorTool.searchExternalJobs",
            "EmailConnectorTool.readEmails",
            "EmailConnectorTool.sendEmail",
            "CalendarConnectorTool.createInterviewEvent",
            "NotificationConnectorTool.sendNotification",
            "ResumeExportConnectorTool.exportResume",
            "JobSourceSyncConnectorTool.syncJobs"
    );

    /*
     * 懒初始化：生产环境由 @PostConstruct 触发；单测直接 new 时也能用，
     * 避免"手工构造后注册表为空"导致所有工具判定未注册。
     */
    private synchronized Map<String, ToolCall> handlers() {
        if (handlers == null) {
            initHandlers();
        }
        return handlers;
    }

    private void register(Map<String, ToolCall> map, String toolName, ToolCall call) {
        if (map.putIfAbsent(toolName, call) != null) {
            throw new IllegalStateException("Agent 工具名重复注册：" + toolName);
        }
    }

    /**
     * 执行指定工具。
     *
     * @param toolName 工具唯一名称
     * @param params Planner 抽取出的参数
     * @param originalMessage 用户原始输入
     * @return 工具统一执行结果
     */
    public AgentToolExecutionResult invoke(
            String toolName,
            Map<String, Object> params,
            String originalMessage
    ) {
        long start = System.currentTimeMillis();

        try {
            // 1. 注册表查找：未登记的工具直接拒绝（白名单语义）
            ToolCall handler = handlers().get(toolName);
            if (handler == null) {
                throw new AgentToolException(
                        AgentToolErrorCode.TOOL_NOT_REGISTERED,
                        toolName,
                        "Executor 不支持调用工具: " + toolName
                );
            }
            String dataJson = handler.execute(params, originalMessage);

            /*
             * 2. 工具输出 JSON 校验。
             *    工具类返回 String 是为了兼容 LangChain4j Tool 调用，但 Executor 不能盲信这个 String。
             *    这里统一校验 JSON 语法和关键字段，避免 Summary Assistant 基于非法输出产生幻觉。
             */
            AgentToolSchema schema = agentToolSchemaRegistry.getRequired(toolName);
            agentGuardrailService.validateToolOutput(toolName, schema, dataJson);

            /*
             * 3. 工具执行成功后统一包装结果。
             *    真实业务 Trace 已经由各 Tool 内部记录，这里只返回给 Executor 更新 step。
             */
            return AgentToolExecutionResult.builder()
                    .success(true)
                    .toolName(toolName)
                    .message("工具执行成功")
                    .dataJson(dataJson)
                    .costTime(System.currentTimeMillis() - start)
                    .build();
        } catch (AgentToolException exception) {
            return AgentToolExecutionResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .errorCode(exception.getToolErrorCode().name())
                    .message(exception.getMessage())
                    .costTime(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception exception) {
            return AgentToolExecutionResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .errorCode(AgentToolErrorCode.TOOL_EXECUTION_FAILED.name())
                    .message(exception.getMessage())
                    .costTime(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private Long firstLong(Map<String, Object> params, String firstKey, String secondKey) {
        Long value = getLong(params, firstKey);
        return value != null ? value : getLong(params, secondKey);
    }

    private String firstString(Map<String, Object> params, String firstKey, String secondKey,
                               String thirdKey, String fallback) {
        String value = getString(params, firstKey);
        if (value != null) {
            return value;
        }
        value = getString(params, secondKey);
        if (value != null) {
            return value;
        }
        value = getString(params, thirdKey);
        return value != null ? value : fallback;
    }

    private Long getLong(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }

    private String getString(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
