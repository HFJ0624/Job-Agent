package com.job.bootstrap.agent.intent;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 作者: hfj
 * 功能: Agent 意图路由器
 *
 * 设计说明:
 * 1. 这里先用关键词规则识别用户意图。
 * 2. 为什么不用大模型识别？
 *    第一版项目要保证稳定性，规则意图识别更容易调试，也更适合简历项目演示。
 * 3. 后续可以升级为:
 *    规则识别优先 + 大模型兜底分类 + JSON 结构化输出。
 */
@Component
public class AgentIntentRouter {

    /**
     * 根据用户输入识别意图。
     *
     * @param message 用户本轮输入
     * @return 意图编码
     */
    public AgentIntentCode route(String message) {
        if (!StringUtils.hasText(message)) {
            return AgentIntentCode.GENERAL_CHAT;
        }

        String text = message.trim().toLowerCase();

        /*
         * 1. 岗位匹配意图。
         * 典型输入:
         * - 帮我看看这份简历和这个岗位匹配吗
         * - resumeId=1 jobId=2 是否适合投递
         */
        if (containsAny(text, "匹配", "适合", "岗位匹配", "是否投递", "能不能投", "推荐投递")) {
            return AgentIntentCode.JOB_MATCH;
        }

        /*
         * 2. 简历分析意图。
         * 典型输入:
         * - 帮我分析一下简历
         * - 我的简历有什么问题
         */
        if (containsAny(text, "分析简历", "简历分析", "优化简历", "简历问题", "简历评分")) {
            return AgentIntentCode.RESUME_ANALYZE;
        }

        /*
         * 3. 岗位搜索/推荐意图。
         * 典型输入:
         * - 帮我找 Java 后端岗位
         * - 推荐几个适合我的岗位
         */
        if (containsAny(text, "找岗位", "搜岗位", "搜索岗位", "推荐岗位", "有哪些岗位", "找工作")) {
            return AgentIntentCode.JOB_SEARCH;
        }

        /*
         * 4. HR 打招呼语意图。
         * 典型输入:
         * - 帮我生成打招呼语
         * - 给 HR 发什么开场白
         */
        if (containsAny(text, "打招呼", "招呼语", "开场白", "hr", "沟通话术")) {
            return AgentIntentCode.GREETING_GENERATE;
        }

        /*
         * 5. 面试复盘意图。
         * 注意：要放在“面试准备”之前，否则“复盘模拟面试”会先命中面试准备。
         */
        if (containsAny(text, "复盘", "面试复盘", "复盘面试")) {
            return AgentIntentCode.MOCK_INTERVIEW;
        }

        /*
         * 6. 面试准备意图。
         * 典型输入:
         * - 帮我准备面试
         * - 根据这个岗位生成面试题
         */
        if (containsAny(text, "面试", "面试题", "模拟面试", "准备面试", "面经")) {
            return AgentIntentCode.INTERVIEW_PREPARE;
        }

        /*
         * 7. 收藏岗位意图。
         */
        if (containsAny(text, "收藏", "加入收藏", "保存岗位")) {
            return AgentIntentCode.JOB_FAVORITE;
        }

        return AgentIntentCode.GENERAL_CHAT;
    }

    /**
     * 判断文本中是否包含任意关键词。
     *
     * @param text 文本
     * @param keywords 关键词数组
     * @return true 表示命中
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
