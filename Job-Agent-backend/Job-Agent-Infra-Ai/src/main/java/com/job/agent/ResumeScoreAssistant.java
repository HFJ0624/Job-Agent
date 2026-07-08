package com.job.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * AI 简历评分 V2 辅助分析 Assistant 接口。
 *
 * <p>核心职责：
 * 作为“第二评分员”，基于简历原文给出独立维度分、等级、优势、不足、风险点和改进建议，
 * 输出严格 JSON，由业务层按权重合并规则引擎分数与模型分数后落库。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的 LangChain4j Assistant 接口层。</p>
 *
 * <p>主要调用链：
 * JobResumeScoreService.score -> ResumeScoreAssistant.analyze
 * -> Jackson 解析 JSON -> ResumeScoreRuleEngine 合并规则分 + 模型分 -> 落库 job_resume_score_record
 * 本接口不参与 Agent 主链路的 Planning / Tool Calling，只服务于简历评分场景。</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>由 ResumeScoreAssistantConfig 注入专用 resumeScoreChatModel，拥有更长超时；</li>
 *   <li>输出 JSON 由业务层解析，模型不可用或解析失败时业务层保留规则引擎输出；</li>
 *   <li>与 ResumeScoreRuleEngine 形成“规则分 + 模型分”双轨评分，避免分数大幅漂移。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 这个 Assistant 作为“第二评分员”，负责基于简历原文给出独立维度分和诊断建议。
 * 2. 最终分数不直接照搬模型输出，而是由业务层按权重合并规则分和模型分，避免分数大幅漂移。
 * 3. 模型必须返回 JSON 字符串，业务层会用 Jackson 解析并合并到最终评分结果中。
 * 4. 如果模型不可用或 JSON 解析失败，业务层会保留规则引擎输出，保证评分功能仍可运行。</p>
 *
 * 作者:hfj
 * 日期:2026/6/15
 */
public interface ResumeScoreAssistant {

    /**
     * 基于简历原文生成结构化评分与诊断建议。
     *
     * <p>核心处理流程：
     * 1. 框架将 SystemMessage 注入为系统提示词，约束模型只输出 JSON；
     * 2. 调用方在 prompt 中拼好简历原文、岗位上下文（可选）；
     * 3. 模型按固定 8 个维度评分，并输出优势、不足、风险点、改进建议；
     * 4. 业务层用 Jackson 解析 JSON，与规则引擎分数按权重合并；
     * 5. 解析失败时业务层降级为纯规则分，保证评分功能可用。</p>
     *
     * @param prompt 已组装的 Prompt，包含简历原文和必要的岗位上下文
     * @return 严格 JSON 字符串，字段定义见 SystemMessage；模型不可用或解析失败时由业务层降级
     */
    @SystemMessage("""
            你是技术简历评分专家。只输出一个 JSON 对象，不要 Markdown。
            评分维度固定为:
            基础信息完整性10、求职目标清晰度10、教育背景10、技能结构15、项目经历质量25、实习 / 工作经历15、成果量化程度10、表达与排版5。
            必须基于简历原文评分，不编造。没有证据写“简历中未找到相关证据”。
            返回字段:
            scoreVersion, overallScore, level, scoreBreakdown, dimensions, strengths, weaknesses, riskPoints, improvementSuggestions, summary。
            每类列表最多 3 条，每条不超过 40 字；summary 不超过 60 字。
            """)
    String analyze(@UserMessage String prompt);
}
