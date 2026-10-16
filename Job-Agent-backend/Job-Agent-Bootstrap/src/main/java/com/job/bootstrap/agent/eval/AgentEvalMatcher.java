package com.job.bootstrap.agent.eval;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 功能: Eval 断言严格匹配器（任务10 质量门禁）。
 *
 * 设计说明:
 * 1. strictMatch 只有“完全一致”才算命中：大小写、全半角、下划线/驼峰、空白差异都视为不匹配，
 *    替换原来宽松的 contains 判断，避免 "Search" 命中 "JobSearchTool"、"job_match_tool" 命中 "JobMatchTool" 等误过。
 * 2. toolStrictMatch 在严格相等基础上，额外允许“类名部分（'.' 前）精确”，
 *    既支持用例按类名断言，又不会被任意子串糊弄。
 */
@Component
public class AgentEvalMatcher {

    public boolean strictMatch(String actual, String expected) {
        if (!StringUtils.hasText(actual) || !StringUtils.hasText(expected)) {
            return false;
        }
        return actual.equals(expected);
    }

    public boolean toolStrictMatch(String actualTool, String expectedTool) {
        if (strictMatch(actualTool, expectedTool)) {
            return true;
        }
        int dotIndex = actualTool == null ? -1 : actualTool.indexOf('.');
        return dotIndex > 0 && strictMatch(actualTool.substring(0, dotIndex), expectedTool);
    }
}
