package com.job.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 作者:hfj
 * 功能:AI 简历评分 V2 辅助分析 Assistant
 * 日期:2026/6/15
 *
 * 设计说明:
 * 1. 这个 Assistant 作为“第二评分员”，负责基于简历原文给出独立维度分和诊断建议。
 * 2. 最终分数不直接照搬模型输出，而是由业务层按权重合并规则分和模型分，避免分数大幅漂移。
 * 3. 模型必须返回 JSON 字符串，业务层会用 Jackson 解析并合并到最终评分结果中。
 * 4. 如果模型不可用或 JSON 解析失败，业务层会保留规则引擎输出，保证评分功能仍可运行。
 */
public interface ResumeScoreAssistant {

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
