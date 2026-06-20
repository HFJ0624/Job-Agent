package com.job.bootstrap.agent.schema;

import com.job.common.agent.tool.AgentToolErrorSchema;
import com.job.common.agent.tool.AgentToolOutputSchema;
import com.job.common.agent.tool.AgentToolParamSchema;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolConfirmationType;
import com.job.enums.AgentToolErrorCode;
import com.job.enums.AgentToolPermissionType;
import com.job.enums.AgentToolSideEffectType;
import com.job.enums.AgentToolValueType;
import com.job.exception.AgentToolException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 作者:hfj
 * 功能:Agent 工具 Schema 注册中心
 * 日期:2026/6/20
 */
@Component
public class AgentToolSchemaRegistry {

    private final Map<String, AgentToolSchema> schemaMap;

    /**
     * 初始化工具 Schema。
     *
     * 说明:
     * 1. 第一版使用代码注册，保证工具定义和 Java Tool 实现一起演进。
     * 2. 后续如果要做后台动态配置，可以把这里的内容迁移到 agent_tool_schema 表。
     */
    public AgentToolSchemaRegistry() {
        Map<String, AgentToolSchema> schemas = new LinkedHashMap<>();
        register(schemas, resumeAnalyzeSchema());
        register(schemas, jobMatchSchema());
        register(schemas, greetingSchema());
        register(schemas, jobSearchSchema());
        register(schemas, jobRecommendSchema());
        register(schemas, interviewPrepareSchema());
        register(schemas, mockInterviewReviewSchema());
        register(schemas, ragSearchSchema());
        this.schemaMap = Collections.unmodifiableMap(schemas);
    }

    /**
     * 查询全部工具 Schema。
     */
    public List<AgentToolSchema> listAll() {
        return List.copyOf(schemaMap.values());
    }

    /**
     * 按工具名查询 Schema。
     */
    public Optional<AgentToolSchema> find(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(schemaMap.get(toolName.trim()));
    }

    /**
     * 按工具名查询 Schema，不存在时抛出统一工具异常。
     */
    public AgentToolSchema getRequired(String toolName) {
        return find(toolName).orElseThrow(() -> new AgentToolException(
                AgentToolErrorCode.TOOL_NOT_REGISTERED,
                toolName,
                "工具未注册 Schema: " + toolName
        ));
    }

    /**
     * 根据计划里的工具表达式查找 Schema。
     *
     * 说明:
     * 1. Planner 里存在 "A / B" 这种候选工具表达式。
     * 2. 这里统一拆分，避免 Planner、ChatService 各写一套解析逻辑。
     */
    public List<AgentToolSchema> findByToolExpression(String toolExpression) {
        if (!StringUtils.hasText(toolExpression)) {
            return List.of();
        }

        List<AgentToolSchema> schemas = new ArrayList<>();
        String[] toolNames = toolExpression.split("/");
        for (String item : toolNames) {
            String toolName = item.trim();
            find(toolName).ifPresent(schemas::add);
        }
        return schemas;
    }

    /**
     * 构造给 Planner 使用的工具输入 Schema。
     *
     * @param toolExpression 单个工具名，或 "工具A / 工具B"
     * @return 可直接序列化到 agent_plan_step.tool_input_schema 的结构
     */
    public Map<String, Object> buildPlanInputSchema(String toolExpression) {
        List<AgentToolSchema> schemas = findByToolExpression(toolExpression);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolExpression", toolExpression);

        if (schemas.isEmpty()) {
            result.put("registered", false);
            return result;
        }

        result.put("registered", true);
        result.put("tools", schemas.stream().map(this::compactSchema).toList());
        return result;
    }

    private void register(Map<String, AgentToolSchema> schemas, AgentToolSchema schema) {
        schemas.put(schema.getToolName(), schema);
    }

    private AgentToolSchema resumeAnalyzeSchema() {
        return tool(
                "ResumeAnalyzeTool.analyzeResume",
                "简历分析工具",
                "resume",
                "对当前登录用户的指定简历做质量评分，输出分数、优势、问题和优化建议。",
                "ResumeAnalyzeTool",
                "analyzeResume",
                AgentToolSideEffectType.WRITE_BUSINESS_RECORD,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("resumeId", AgentToolValueType.LONG, true, "简历ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "1", null),
                        param("targetPosition", AgentToolValueType.STRING, false, "求职方向", "USER_INPUT", "Java 后端开发", null)
                ),
                List.of(
                        output("resumeId", AgentToolValueType.LONG, "简历ID", false, "1"),
                        output("totalScore", AgentToolValueType.INTEGER, "简历总分", true, "86"),
                        output("advantages", AgentToolValueType.ARRAY, "简历优势列表", true, "[\"项目经历完整\"]"),
                        output("problems", AgentToolValueType.ARRAY, "问题列表", true, "[\"量化结果不足\"]"),
                        output("suggestions", AgentToolValueType.ARRAY, "优化建议列表", true, "[\"补充项目指标\"]")
                )
        );
    }

    private AgentToolSchema jobMatchSchema() {
        return tool(
                "JobMatchTool.matchJob",
                "岗位匹配工具",
                "job",
                "分析当前用户某份简历与指定岗位的匹配度，输出匹配分、优势、风险和投递建议。",
                "JobMatchTool",
                "matchJob",
                AgentToolSideEffectType.WRITE_BUSINESS_RECORD,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("resumeId", AgentToolValueType.LONG, true, "简历ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "1", null),
                        param("jobId", AgentToolValueType.LONG, true, "岗位ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "2", null)
                ),
                List.of(
                        output("matchScore", AgentToolValueType.INTEGER, "匹配分", true, "82"),
                        output("matchLevel", AgentToolValueType.STRING, "匹配等级", true, "HIGH"),
                        output("matchedSkills", AgentToolValueType.ARRAY, "已匹配技能", true, "[\"Java\", \"Spring Boot\"]"),
                        output("missingSkills", AgentToolValueType.ARRAY, "缺失技能", true, "[\"Redis\"]"),
                        output("suggestions", AgentToolValueType.ARRAY, "投递或优化建议", true, "[\"建议补充缓存项目经验\"]")
                )
        );
    }

    private AgentToolSchema greetingSchema() {
        return tool(
                "GreetingGenerateTool.generateGreeting",
                "HR 打招呼语生成工具",
                "greeting",
                "根据用户简历和岗位生成 HR 开场白，并自动创建沟通记录。",
                "GreetingGenerateTool",
                "generateGreeting",
                AgentToolSideEffectType.WRITE_BUSINESS_RECORD,
                AgentToolConfirmationType.REQUIRED_BEFORE_EXECUTION,
                "该工具会生成 HR 打招呼语，并自动创建一条沟通记录。确认后才会执行。",
                List.of(
                        param("resumeId", AgentToolValueType.LONG, true, "简历ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "1", null),
                        param("jobId", AgentToolValueType.LONG, true, "岗位ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "2", null),
                        param("style", AgentToolValueType.STRING, false, "语气风格", "USER_INPUT", "自然", "自然")
                ),
                List.of(
                        output("id", AgentToolValueType.LONG, "打招呼语记录ID", true, "10"),
                        output("content", AgentToolValueType.STRING, "可复制给 HR 的话术", false, "您好，我对贵公司的岗位比较感兴趣..."),
                        output("matchedSkills", AgentToolValueType.ARRAY, "话术引用的匹配技能", true, "[\"Java\"]"),
                        output("source", AgentToolValueType.STRING, "生成来源", true, "RULE")
                )
        );
    }

    private AgentToolSchema jobSearchSchema() {
        return tool(
                "JobSearchTool.searchJobs",
                "岗位搜索工具",
                "job",
                "按关键词、城市、学历和经验搜索已发布岗位。",
                "JobSearchTool",
                "searchJobs",
                AgentToolSideEffectType.READ_ONLY,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("keyword", AgentToolValueType.STRING, false, "岗位关键词", "USER_INPUT", "Java 后端", null),
                        param("city", AgentToolValueType.STRING, false, "城市", "USER_INPUT", "上海", null),
                        param("educationReq", AgentToolValueType.STRING, false, "学历要求", "USER_INPUT", "本科", null),
                        param("experienceReq", AgentToolValueType.STRING, false, "经验要求", "USER_INPUT", "1-3年", null)
                ),
                List.of(
                        output("total", AgentToolValueType.LONG, "命中岗位总数", false, "12"),
                        output("jobs", AgentToolValueType.ARRAY, "岗位列表", false, "[{\"jobId\":1,\"jobTitle\":\"Java 开发\"}]")
                )
        );
    }

    private AgentToolSchema jobRecommendSchema() {
        return tool(
                "JobRecommendTool.recommendJobs",
                "岗位推荐工具",
                "job",
                "基于当前用户求职偏好推荐岗位。",
                "JobRecommendTool",
                "recommendJobs",
                AgentToolSideEffectType.READ_ONLY,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("keyword", AgentToolValueType.STRING, false, "岗位关键词", "USER_INPUT", "AI 应用开发", null),
                        param("city", AgentToolValueType.STRING, false, "城市", "USER_INPUT", "杭州", null),
                        param("limit", AgentToolValueType.INTEGER, false, "推荐数量", "USER_INPUT", "5", "10")
                ),
                List.of(
                        output("records", AgentToolValueType.ARRAY, "推荐岗位列表", true, "[{\"jobId\":1,\"recommendScore\":90}]")
                )
        );
    }

    private AgentToolSchema interviewPrepareSchema() {
        return tool(
                "InterviewPrepareTool.prepareInterview",
                "面试准备工具",
                "interview",
                "根据求职投递记录生成面试题、复习建议和项目追问。",
                "InterviewPrepareTool",
                "prepareInterview",
                AgentToolSideEffectType.WRITE_BUSINESS_RECORD,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("applicationId", AgentToolValueType.LONG, true, "投递记录ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "1", null),
                        param("resumeId", AgentToolValueType.LONG, false, "简历ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "1", null)
                ),
                List.of(
                        output("id", AgentToolValueType.LONG, "面试准备记录ID", true, "5"),
                        output("technicalQuestions", AgentToolValueType.ARRAY, "技术题列表", true, "[\"Spring Bean 生命周期是什么？\"]"),
                        output("projectQuestions", AgentToolValueType.ARRAY, "项目追问题列表", true, "[\"你的项目如何做权限控制？\"]"),
                        output("reviewSuggestions", AgentToolValueType.ARRAY, "复习建议", true, "[\"复习 JVM 和 Redis\"]")
                )
        );
    }

    private AgentToolSchema mockInterviewReviewSchema() {
        return tool(
                "MockInterviewReviewTool.generateMockInterviewReview",
                "模拟面试复盘工具",
                "interview",
                "根据模拟面试会话生成表现复盘、薄弱题和提升计划。",
                "MockInterviewReviewTool",
                "generateMockInterviewReview",
                AgentToolSideEffectType.WRITE_BUSINESS_RECORD,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("sessionId", AgentToolValueType.LONG, true, "模拟面试会话ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "1", null)
                ),
                List.of(
                        output("totalScore", AgentToolValueType.INTEGER, "复盘总分", true, "78"),
                        output("level", AgentToolValueType.STRING, "表现等级", true, "中等"),
                        output("advantages", AgentToolValueType.ARRAY, "优势", true, "[\"表达清晰\"]"),
                        output("weaknesses", AgentToolValueType.ARRAY, "短板", true, "[\"项目细节不够\"]"),
                        output("improvementPlan", AgentToolValueType.ARRAY, "提升计划", true, "[\"补充项目复盘\"]")
                )
        );
    }

    private AgentToolSchema ragSearchSchema() {
        return tool(
                "RagSearchTool.searchKnowledge",
                "RAG 知识库检索工具",
                "rag",
                "检索当前用户私有求职知识和公共岗位公司知识。",
                "RagSearchTool",
                "searchKnowledge",
                AgentToolSideEffectType.READ_ONLY,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("query", AgentToolValueType.STRING, true, "检索问题", "USER_INPUT", "我的项目经历和岗位匹配吗？", null),
                        param("limit", AgentToolValueType.INTEGER, false, "召回条数", "USER_INPUT", "5", "5")
                ),
                List.of(
                        output("total", AgentToolValueType.INTEGER, "召回条数", false, "3"),
                        output("results", AgentToolValueType.ARRAY, "召回知识片段", false, "[{\"documentType\":\"RESUME\",\"content\":\"...\"}]")
                )
        );
    }

    private AgentToolSchema tool(
            String toolName,
            String displayName,
            String category,
            String description,
            String javaClassName,
            String javaMethodName,
            AgentToolSideEffectType sideEffectType,
            AgentToolConfirmationType confirmationType,
            String confirmationMessage,
            List<AgentToolParamSchema> inputParams,
            List<AgentToolOutputSchema> outputFields
    ) {
        boolean requiresConfirmation = AgentToolConfirmationType.REQUIRED_BEFORE_EXECUTION.equals(confirmationType);
        boolean hasSideEffect = !AgentToolSideEffectType.READ_ONLY.equals(sideEffectType);

        return AgentToolSchema.builder()
                .toolName(toolName)
                .displayName(displayName)
                .category(category)
                .version("v1")
                .description(description)
                .javaClassName(javaClassName)
                .javaMethodName(javaMethodName)
                .permissionType(AgentToolPermissionType.LOGIN_USER)
                .sideEffectType(sideEffectType)
                .hasSideEffect(hasSideEffect)
                .confirmationType(confirmationType)
                .requiresUserConfirmation(requiresConfirmation)
                .confirmationMessage(confirmationMessage)
                .inputParams(inputParams)
                .outputFields(outputFields)
                .errorCodes(commonErrors(requiresConfirmation))
                .build();
    }

    private AgentToolParamSchema param(
            String name,
            AgentToolValueType type,
            boolean required,
            String description,
            String source,
            String example,
            String defaultValue
    ) {
        return AgentToolParamSchema.builder()
                .name(name)
                .type(type)
                .required(required)
                .description(description)
                .source(source)
                .example(example)
                .defaultValue(defaultValue)
                .sensitive(false)
                .build();
    }

    private AgentToolOutputSchema output(
            String name,
            AgentToolValueType type,
            String description,
            boolean nullable,
            String example
    ) {
        return AgentToolOutputSchema.builder()
                .name(name)
                .type(type)
                .description(description)
                .nullable(nullable)
                .example(example)
                .build();
    }

    private List<AgentToolErrorSchema> commonErrors(boolean includeConfirmationError) {
        List<AgentToolErrorSchema> errors = new ArrayList<>();
        errors.add(error(AgentToolErrorCode.TOOL_CONTEXT_MISSING, "缺少 Agent 运行时上下文", "当前会话状态异常，请稍后重试", false));
        errors.add(error(AgentToolErrorCode.TOOL_PARAM_MISSING, "缺少工具必填参数", "请补充必要信息后再试", true));
        errors.add(error(AgentToolErrorCode.TOOL_PERMISSION_DENIED, "当前身份无权调用工具", "你没有权限执行该操作", false));
        if (includeConfirmationError) {
            errors.add(error(AgentToolErrorCode.TOOL_CONFIRMATION_REQUIRED, "工具需要用户确认", "该操作需要你确认后才能执行", true));
        }
        errors.add(error(AgentToolErrorCode.TOOL_EXECUTION_FAILED, "工具业务执行失败", "工具执行失败，请稍后重试", true));
        return errors;
    }

    private AgentToolErrorSchema error(
            AgentToolErrorCode code,
            String message,
            String userMessage,
            boolean retryable
    ) {
        return AgentToolErrorSchema.builder()
                .code(code)
                .message(message)
                .userMessage(userMessage)
                .retryable(retryable)
                .build();
    }

    private Map<String, Object> compactSchema(AgentToolSchema schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("toolName", schema.getToolName());
        map.put("displayName", schema.getDisplayName());
        map.put("permissionType", schema.getPermissionType());
        map.put("sideEffectType", schema.getSideEffectType());
        map.put("hasSideEffect", schema.getHasSideEffect());
        map.put("requiresUserConfirmation", schema.getRequiresUserConfirmation());
        map.put("confirmationMessage", schema.getConfirmationMessage());
        map.put("inputParams", schema.getInputParams());
        map.put("outputFields", schema.getOutputFields());
        map.put("errorCodes", schema.getErrorCodes());
        return map;
    }
}
