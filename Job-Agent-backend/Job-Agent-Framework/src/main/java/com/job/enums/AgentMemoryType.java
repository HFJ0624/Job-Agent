package com.job.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆类型
 * 日期:2026/6/20
 *
 * 说明:
 * 1. 这里按“Agent 可以长期使用的事实”分类，而不是按一次对话分类。
 * 2. 第一版先覆盖求职 Agent 最核心的记忆: 用户偏好、简历画像、面试反馈和历史决策。
 * 3. 后续如果接入向量检索，可以直接把 memoryType 作为文档类型或过滤条件使用。
 */
@Getter
@RequiredArgsConstructor
public enum AgentMemoryType {

    /**
     * 用户显式或隐式表达的求职偏好，例如城市、岗位方向、薪资下限。
     */
    USER_PREFERENCE("用户偏好"),

    /**
     * 从简历解析、简历评分或简历分析工具中沉淀出的用户画像。
     */
    RESUME_PROFILE("简历画像"),

    /**
     * 从面试准备、模拟面试复盘中沉淀出的表现反馈。
     */
    INTERVIEW_FEEDBACK("面试反馈"),

    /**
     * 对某个岗位是否值得投递、是否匹配等历史判断。
     */
    JOB_DECISION("岗位决策"),

    /**
     * 用户偏好的沟通风格，例如简洁、真诚、积极、正式。
     */
    COMMUNICATION_STYLE("沟通风格"),

    /**
     * 简历分析、岗位匹配、面试复盘中反复出现的能力短板。
     */
    SKILL_GAP("能力短板"),

    /**
     * 用户长期职业目标，例如目标行业、目标岗位、发展方向。
     */
    CAREER_GOAL("职业目标");

    private final String description;
}
