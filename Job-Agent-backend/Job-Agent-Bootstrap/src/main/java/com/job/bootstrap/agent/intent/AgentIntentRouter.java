package com.job.bootstrap.agent.intent;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 作者: hfj
 * 功能: Agent 意图路由器（规则优先）
 *
 * 设计说明:
 * 1. 第一版用关键词规则识别意图，稳定、可调试、零成本。
 * 2. route() 只返回意图编码，兼容旧调用与测试。
 * 3. routeDetailed() 额外返回 ruleMatched，标记是否真被某条规则命中；
 *    没命中（回落 GENERAL_CHAT）时，上层用 {@link AgentIntentLlmClassifier} 做 LLM 兜底，
 *    即“规则优先 + 大模型兜底分类”。
 */
@Component
public class AgentIntentRouter {

    /**
     * 规则路由结果。
     *
     * @param intentCode  识别出的意图（没命中时为 GENERAL_CHAT）
     * @param ruleMatched true 表示命中某条关键词规则；false 表示回落默认
     */
    public record IntentResolution(AgentIntentCode intentCode, boolean ruleMatched) {
    }

    /**
     * 根据用户输入识别意图（仅规则，兼容旧入口）。
     */
    public AgentIntentCode route(String message) {
        return routeDetailed(message).intentCode();
    }

    /**
     * 根据用户输入识别意图，并标记是否被规则命中，供上层决定是否走 LLM 兜底。
     */
    public IntentResolution routeDetailed(String message) {
        if (!StringUtils.hasText(message)) {
            return new IntentResolution(AgentIntentCode.GENERAL_CHAT, false);
        }

        String text = message.trim().toLowerCase();

        /*
         * 1. 岗位匹配意图。
         * 典型输入:
         * - 帮我看看这份简历和这个岗位匹配吗
         * - resumeId=1 jobId=2 是否适合投递
         */
        if (containsAny(text, "匹配", "适合", "岗位匹配", "是否投递", "能不能投", "推荐投递")) {
            return new IntentResolution(AgentIntentCode.JOB_MATCH, true);
        }

        /*
         * 2. 简历分析意图。
         */
        if (containsAny(text, "分析简历", "简历分析", "优化简历", "简历问题", "简历评分")) {
            return new IntentResolution(AgentIntentCode.RESUME_ANALYZE, true);
        }

        /*
         * 3. 岗位搜索/推荐意图。
         */
        if (containsAny(text, "找岗位", "搜岗位", "搜索岗位", "推荐岗位", "有哪些岗位", "找工作")) {
            return new IntentResolution(AgentIntentCode.JOB_SEARCH, true);
        }

        /*
         * 4. HR 打招呼语意图。
         */
        if (containsAny(text, "打招呼", "招呼语", "开场白", "hr", "沟通话术")) {
            return new IntentResolution(AgentIntentCode.GREETING_GENERATE, true);
        }

        /*
         * 5. 面试复盘意图。
         * 注意：要放在“面试准备”之前，否则“复盘模拟面试”会先命中面试准备。
         */
        if (containsAny(text, "复盘", "面试复盘", "复盘面试")) {
            return new IntentResolution(AgentIntentCode.MOCK_INTERVIEW, true);
        }

        /*
         * 6. 面试准备意图。
         */
        if (containsAny(text, "面试", "面试题", "模拟面试", "准备面试", "面经")) {
            return new IntentResolution(AgentIntentCode.INTERVIEW_PREPARE, true);
        }

        /*
         * 7. 收藏岗位意图。
         */
        if (containsAny(text, "收藏", "加入收藏", "保存岗位")) {
            return new IntentResolution(AgentIntentCode.JOB_FAVORITE, true);
        }

        // 没命中任何规则：回落 GENERAL_CHAT，ruleMatched=false 让上层走 LLM 兜底
        return new IntentResolution(AgentIntentCode.GENERAL_CHAT, false);
    }

    /**
     * 判断文本中是否包含任意关键词。
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
