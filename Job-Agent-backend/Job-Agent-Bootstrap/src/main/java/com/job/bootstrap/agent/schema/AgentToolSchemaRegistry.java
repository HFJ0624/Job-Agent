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
    private final Map<String, String> aliasMap;

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
        register(schemas, recruitmentPlatformSearchSchema());
        register(schemas, emailReadSchema());
        register(schemas, emailSendSchema());
        register(schemas, calendarCreateSchema());
        register(schemas, notificationSendSchema());
        register(schemas, resumeExportSchema());
        register(schemas, jobSourceSyncSchema());
        this.schemaMap = Collections.unmodifiableMap(schemas);
        this.aliasMap = Collections.unmodifiableMap(buildAliasMap(schemas));
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
        return resolveToolName(toolName)
                .map(schemaMap::get);
    }

    /**
     * 将外部传入的工具名统一解析成标准工具名。
     *
     * 解析规则:
     * 1. 标准工具名固定使用 ClassName.methodName，例如 RagSearchTool.searchKnowledge。
     * 2. 为兼容 Planner、Eval、后台配置里的历史短名，允许 ClassName 解析到唯一标准工具名。
     * 3. 不支持只写 methodName，避免不同工具方法重名时误调用。
     *
     * @param toolName 外部传入的工具名或历史短名
     * @return 标准工具名
     */
    public Optional<String> resolveToolName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliasMap.get(toolName.trim()));
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
            find(item).ifPresent(schemas::add);
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

    private Map<String, String> buildAliasMap(Map<String, AgentToolSchema> schemas) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (AgentToolSchema schema : schemas.values()) {
            /*
             * 这里统一把所有可接受的历史写法映射到 ClassName.methodName。
             * 后续 Guardrails、Executor、Trace 都只使用标准工具名，避免同一个工具出现多种名字。
             */
            putAlias(aliases, schema.getToolName(), schema.getToolName());
            putAlias(aliases, schema.getJavaClassName(), schema.getToolName());
            putAlias(aliases, schema.getJavaClassName() + "." + schema.getJavaMethodName(), schema.getToolName());
        }
        return aliases;
    }

    private void putAlias(Map<String, String> aliases, String alias, String toolName) {
        if (StringUtils.hasText(alias) && StringUtils.hasText(toolName)) {
            aliases.put(alias.trim(), toolName.trim());
        }
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
                        output("reviewLevel", AgentToolValueType.STRING, "表现等级", true, "中等"),
                        output("strengthSummary", AgentToolValueType.STRING, "优势总结", true, "表达清晰"),
                        output("weaknessSummary", AgentToolValueType.STRING, "短板总结", true, "项目细节不够"),
                        output("improvementPlan", AgentToolValueType.STRING, "提升计划", true, "补充项目复盘")
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

    private AgentToolSchema recruitmentPlatformSearchSchema() {
        return externalConnectorTool(
                "RecruitmentPlatformConnectorTool.searchExternalJobs",
                "外部招聘平台岗位搜索",
                "从 Boss、猎聘、拉勾等外部招聘平台搜索岗位。第二版只返回预览结果，不真实请求平台。",
                "RecruitmentPlatformConnectorTool",
                "searchExternalJobs",
                AgentToolSideEffectType.READ_ONLY,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("providerCode", AgentToolValueType.STRING, true, "招聘平台编码，例如 boss、liepin、lagou", "USER_INPUT", "boss", null),
                        param("keyword", AgentToolValueType.STRING, false, "岗位关键词", "USER_INPUT", "Java", null),
                        param("city", AgentToolValueType.STRING, false, "城市", "USER_INPUT", "上海", null),
                        param("limit", AgentToolValueType.INTEGER, false, "返回数量上限", "USER_INPUT", "10", "10")
                )
        );
    }

    private AgentToolSchema emailReadSchema() {
        return externalConnectorTool(
                "EmailConnectorTool.readEmails",
                "邮箱读取",
                "读取邮箱中的求职相关邮件。第二版只返回预览结果，不真实连接邮箱。",
                "EmailConnectorTool",
                "readEmails",
                AgentToolSideEffectType.READ_ONLY,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("providerCode", AgentToolValueType.STRING, true, "邮箱渠道编码，例如 qq-mail、gmail、outlook", "USER_INPUT", "qq-mail", null),
                        param("keyword", AgentToolValueType.STRING, false, "搜索关键词", "USER_INPUT", "面试", null),
                        param("limit", AgentToolValueType.INTEGER, false, "读取数量上限", "USER_INPUT", "5", "10")
                )
        );
    }

    private AgentToolSchema emailSendSchema() {
        return externalConnectorTool(
                "EmailConnectorTool.sendEmail",
                "邮箱发送",
                "发送求职沟通邮件。该操作会触达外部收件人，必须用户确认；第二版只返回预览结果。",
                "EmailConnectorTool",
                "sendEmail",
                AgentToolSideEffectType.EXTERNAL_ACTION,
                AgentToolConfirmationType.REQUIRED_BEFORE_EXECUTION,
                "发送邮件会触达外部收件人，请确认收件人、标题和正文无误后再执行。",
                List.of(
                        param("providerCode", AgentToolValueType.STRING, true, "邮箱渠道编码", "USER_INPUT", "qq-mail", null),
                        param("to", AgentToolValueType.STRING, true, "收件人邮箱", "USER_INPUT", "hr@example.com", null),
                        param("subject", AgentToolValueType.STRING, true, "邮件标题", "USER_INPUT", "确认面试时间", null),
                        param("content", AgentToolValueType.STRING, true, "邮件正文", "USER_INPUT", "您好，我可以参加面试。", null)
                )
        );
    }

    private AgentToolSchema calendarCreateSchema() {
        return externalConnectorTool(
                "CalendarConnectorTool.createInterviewEvent",
                "创建面试日历",
                "创建外部日历事件。该操作会写入外部日历，必须用户确认；第二版只返回预览结果。",
                "CalendarConnectorTool",
                "createInterviewEvent",
                AgentToolSideEffectType.EXTERNAL_ACTION,
                AgentToolConfirmationType.REQUIRED_BEFORE_EXECUTION,
                "创建日历事件会写入外部日历，请确认标题、时间和地点无误后再执行。",
                List.of(
                        param("providerCode", AgentToolValueType.STRING, true, "日历渠道编码", "USER_INPUT", "google-calendar", null),
                        param("title", AgentToolValueType.STRING, true, "事件标题", "USER_INPUT", "后端一面", null),
                        param("startTime", AgentToolValueType.STRING, true, "开始时间", "USER_INPUT", "2026-07-01 10:00", null),
                        param("location", AgentToolValueType.STRING, false, "地点或会议链接", "USER_INPUT", "线上会议", null)
                )
        );
    }

    private AgentToolSchema notificationSendSchema() {
        return externalConnectorTool(
                "NotificationConnectorTool.sendNotification",
                "发送通知",
                "通过站内信、邮件、短信、企业微信或钉钉发送通知。该操作会触达用户，必须用户确认。",
                "NotificationConnectorTool",
                "sendNotification",
                AgentToolSideEffectType.EXTERNAL_ACTION,
                AgentToolConfirmationType.REQUIRED_BEFORE_EXECUTION,
                "发送通知会触达用户或外部渠道，请确认接收人和内容无误后再执行。",
                List.of(
                        param("channel", AgentToolValueType.STRING, true, "通知渠道", "USER_INPUT", "email", null),
                        param("receiver", AgentToolValueType.STRING, true, "接收人标识", "USER_INPUT", "user-1", null),
                        param("title", AgentToolValueType.STRING, true, "通知标题", "USER_INPUT", "面试提醒", null),
                        param("content", AgentToolValueType.STRING, true, "通知内容", "USER_INPUT", "明天 10 点面试", null)
                )
        );
    }

    private AgentToolSchema resumeExportSchema() {
        return externalConnectorTool(
                "ResumeExportConnectorTool.exportResume",
                "简历导出",
                "把用户简历导出为 PDF、Word 或 Markdown。第二版只返回预览结果，不生成真实文件。",
                "ResumeExportConnectorTool",
                "exportResume",
                AgentToolSideEffectType.WRITE_BUSINESS_RECORD,
                AgentToolConfirmationType.NONE,
                null,
                List.of(
                        param("resumeId", AgentToolValueType.LONG, true, "简历ID", "USER_INPUT_OR_FRONTEND_CONTEXT", "1001", null),
                        param("format", AgentToolValueType.STRING, true, "导出格式，例如 PDF、DOCX、MARKDOWN", "USER_INPUT", "PDF", null)
                )
        );
    }

    private AgentToolSchema jobSourceSyncSchema() {
        return externalConnectorTool(
                "JobSourceSyncConnectorTool.syncJobs",
                "岗位来源同步",
                "从外部招聘平台同步岗位到本地岗位库。该操作会写入本地数据，必须用户确认；第二版只返回预览结果。",
                "JobSourceSyncConnectorTool",
                "syncJobs",
                AgentToolSideEffectType.EXTERNAL_ACTION,
                AgentToolConfirmationType.REQUIRED_BEFORE_EXECUTION,
                "岗位同步会写入本地岗位库并可能触发索引，请确认来源和条件后再执行。",
                List.of(
                        param("providerCode", AgentToolValueType.STRING, true, "岗位来源编码", "USER_INPUT", "boss", null),
                        param("keyword", AgentToolValueType.STRING, false, "同步关键词", "USER_INPUT", "Java", null),
                        param("city", AgentToolValueType.STRING, false, "同步城市", "USER_INPUT", "上海", null),
                        param("limit", AgentToolValueType.INTEGER, false, "同步数量上限", "USER_INPUT", "20", "20")
                )
        );
    }

    private AgentToolSchema externalConnectorTool(
            String toolName,
            String displayName,
            String description,
            String javaClassName,
            String javaMethodName,
            AgentToolSideEffectType sideEffectType,
            AgentToolConfirmationType confirmationType,
            String confirmationMessage,
            List<AgentToolParamSchema> inputParams
    ) {
        return tool(
                toolName,
                displayName,
                "external_connector",
                description,
                javaClassName,
                javaMethodName,
                sideEffectType,
                confirmationType,
                confirmationMessage,
                inputParams,
                List.of(
                        output("toolName", AgentToolValueType.STRING, "工具唯一名称", false, toolName),
                        output("connectorType", AgentToolValueType.STRING, "连接器类型", false, "email"),
                        output("providerCode", AgentToolValueType.STRING, "平台或渠道编码", false, "boss"),
                        output("status", AgentToolValueType.STRING, "执行状态，第二版固定为 PREVIEW", false, "PREVIEW"),
                        output("sideEffectType", AgentToolValueType.STRING, "连接器副作用类型", false, "READ"),
                        output("requiresUserConfirmation", AgentToolValueType.BOOLEAN, "是否需要用户确认", false, "true"),
                        output("requiresRealAdapter", AgentToolValueType.BOOLEAN, "是否还需要真实第三方适配器", false, "true"),
                        output("message", AgentToolValueType.STRING, "执行说明", false, "已生成预览"),
                        output("request", AgentToolValueType.OBJECT, "结构化请求参数", false, "{\"providerCode\":\"boss\"}"),
                        output("data", AgentToolValueType.OBJECT, "预览数据", false, "{}")
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
        errors.add(error(AgentToolErrorCode.TOOL_OUTPUT_INVALID_JSON, "工具输出不是合法 JSON", "工具返回格式异常，请稍后重试", true));
        errors.add(error(AgentToolErrorCode.TOOL_OUTPUT_SCHEMA_MISMATCH, "工具输出和 Schema 不一致", "工具返回字段异常，请稍后重试", true));
        errors.add(error(AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED, "工具被 Guardrails 拦截", "该操作没有通过安全校验", false));
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
