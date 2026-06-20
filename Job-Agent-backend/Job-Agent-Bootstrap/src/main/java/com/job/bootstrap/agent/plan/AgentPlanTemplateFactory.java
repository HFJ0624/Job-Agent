package com.job.bootstrap.agent.plan;

import com.job.bootstrap.agent.intent.AgentIntentCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:Agent 计划模板工厂
 * 日期:2026/6/19
 */
@Component
public class AgentPlanTemplateFactory {

    /**
     * 根据意图生成计划模板。
     *
     * @param intentCode 意图编码
     * @param userGoal 用户目标
     * @return 计划模板
     */
    public AgentPlanTemplate create(AgentIntentCode intentCode, String userGoal) {
        return switch (intentCode) {
            case RESUME_ANALYZE -> resumeAnalyzePlan();
            case JOB_MATCH -> jobMatchPlan();
            case GREETING_GENERATE -> greetingPlan();
            case JOB_SEARCH -> jobSearchPlan();
            case INTERVIEW_PREPARE -> interviewPreparePlan();
            case MOCK_INTERVIEW -> mockInterviewReviewPlan();
            case JOB_FAVORITE -> jobFavoritePlan();
            default -> generalChatPlan();
        };
    }

    private AgentPlanTemplate resumeAnalyzePlan() {
        return new AgentPlanTemplate(
                "简历分析计划",
                "校验用户选择的简历，调用简历分析工具，输出评分、优势、问题和优化建议。",
                List.of("resumeId"),
                List.of(
                        step("确认简历", "确认用户提供的 resumeId 属于当前登录用户。", "ResumeAnalyzeTool.analyzeResume",
                                Map.of("resumeId", "必填，用户简历ID", "targetPosition", "可选，求职方向"),
                                "确认简历可解析且能用于评分。"),
                        step("生成分析结果", "基于简历原文输出质量评分和优化建议。", "ResumeAnalyzeTool.analyzeResume",
                                Map.of("resumeId", "来自已抽取参数"),
                                "返回总分、优势、不足和优化建议。")
                )
        );
    }

    private AgentPlanTemplate jobMatchPlan() {
        return new AgentPlanTemplate(
                "岗位匹配计划",
                "校验简历和岗位，调用岗位匹配工具，判断是否值得投递。",
                List.of("resumeId", "jobId"),
                List.of(
                        step("确认匹配对象", "确认 resumeId 和 jobId 都已提供。", "JobMatchTool.matchJob",
                                Map.of("resumeId", "必填", "jobId", "必填"),
                                "简历和岗位均存在且可以进入匹配。"),
                        step("执行岗位匹配", "计算技能、项目、条件和偏好匹配度。", "JobMatchTool.matchJob",
                                Map.of("resumeId", "来自已抽取参数", "jobId", "来自已抽取参数"),
                                "返回匹配分、匹配等级、优势、风险点和建议。")
                )
        );
    }

    private AgentPlanTemplate greetingPlan() {
        return new AgentPlanTemplate(
                "HR 打招呼语计划",
                "校验简历和岗位，生成适合发给 HR 的开场白，并形成沟通记录。",
                List.of("resumeId", "jobId"),
                List.of(
                        step("确认话术上下文", "确认简历、岗位和话术风格。", "GreetingGenerateTool.generateGreeting",
                                Map.of("resumeId", "必填", "jobId", "必填", "style", "可选"),
                                "确认生成话术所需上下文完整。"),
                        step("生成沟通话术", "生成自然、克制、与岗位匹配的 HR 开场白。", "GreetingGenerateTool.generateGreeting",
                                Map.of("resumeId", "来自已抽取参数", "jobId", "来自已抽取参数"),
                                "返回可复制的话术，并由后端自动创建沟通记录。")
                )
        );
    }

    private AgentPlanTemplate jobSearchPlan() {
        return new AgentPlanTemplate(
                "岗位搜索/推荐计划",
                "解析岗位关键词、城市和薪资条件，调用岗位搜索或岗位推荐工具。",
                List.of(),
                List.of(
                        step("解析搜索条件", "从用户输入中提取岗位关键词、城市和最低薪资。", null,
                                Map.of("keyword", "可选", "city", "可选", "minSalary", "可选"),
                                "得到可用于岗位检索的筛选条件。"),
                        step("检索岗位", "按条件搜索岗位；如果用户强调偏好推荐，则优先调用推荐工具。", "JobSearchTool.searchJobs / JobRecommendTool.recommendJobs",
                                Map.of("keyword", "可选", "city", "可选", "minSalary", "可选"),
                                "返回岗位列表、公司、薪资、地点和推荐理由。")
                )
        );
    }

    private AgentPlanTemplate interviewPreparePlan() {
        return new AgentPlanTemplate(
                "面试准备计划",
                "根据投递记录和关联岗位生成面试准备材料。",
                List.of("applicationId"),
                List.of(
                        step("确认投递记录", "确认 applicationId 属于当前用户。", "InterviewPrepareTool.prepareInterview",
                                Map.of("applicationId", "必填", "resumeId", "可选"),
                                "确认投递记录可用于生成面试准备。"),
                        step("生成面试材料", "生成技术题、项目追问题、HR 问题和复习建议。", "InterviewPrepareTool.prepareInterview",
                                Map.of("applicationId", "来自已抽取参数"),
                                "返回面试准备材料和复习建议。")
                )
        );
    }

    private AgentPlanTemplate mockInterviewReviewPlan() {
        return new AgentPlanTemplate(
                "模拟面试复盘计划",
                "根据模拟面试会话生成复盘总结和提升计划。",
                List.of("mockSessionId"),
                List.of(
                        step("确认模拟面试会话", "确认 mockSessionId 属于当前用户。", "MockInterviewReviewTool.generateMockInterviewReview",
                                Map.of("mockSessionId", "必填"),
                                "确认模拟面试会话已存在且可复盘。"),
                        step("生成复盘", "汇总回答表现、薄弱题目和提升建议。", "MockInterviewReviewTool.generateMockInterviewReview",
                                Map.of("mockSessionId", "来自已抽取参数"),
                                "返回总分、优势、短板、薄弱题和提升计划。")
                )
        );
    }

    private AgentPlanTemplate jobFavoritePlan() {
        return new AgentPlanTemplate(
                "岗位收藏计划",
                "确认岗位 ID 后，提示用户当前 Agent 暂不直接执行收藏操作。",
                List.of("jobId"),
                List.of(
                        step("确认岗位", "确认用户要收藏的岗位 ID。", null,
                                Map.of("jobId", "必填"),
                                "明确岗位 ID，并提示用户可在岗位详情页收藏。")
                )
        );
    }

    private AgentPlanTemplate generalChatPlan() {
        return new AgentPlanTemplate(
                "通用求职问答计划",
                "先检索用户知识库，再基于召回知识或通用求职经验回答。",
                List.of(),
                List.of(
                        step("检索知识库", "检索当前用户简历、岗位、公司和沟通记录。", "RagSearchTool.searchKnowledge",
                                Map.of("query", "用户原始问题", "limit", "建议3到5"),
                                "获得可用于回答的相关知识片段。"),
                        step("生成回答", "基于知识片段回答用户问题，知识不足时明确说明。", null,
                                Map.of("answerLanguage", "中文"),
                                "输出清晰、可信、不过度编造的求职建议。")
                )
        );
    }

    private AgentPlanStepTemplate step(
            String stepName,
            String stepGoal,
            String toolName,
            Map<String, Object> toolInputSchema,
            String completionCriteria
    ) {
        return new AgentPlanStepTemplate(stepName, stepGoal, toolName, toolInputSchema, completionCriteria);
    }
}
