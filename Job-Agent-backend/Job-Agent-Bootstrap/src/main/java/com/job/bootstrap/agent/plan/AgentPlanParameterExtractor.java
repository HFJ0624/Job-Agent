package com.job.bootstrap.agent.plan;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 作者: hfj
 * 功能: Agent 计划参数抽取器
 * 日期: 2026/6/19
 */
@Component
public class AgentPlanParameterExtractor {

    private static final Pattern RESUME_ID_PATTERN = Pattern.compile(
            "(?i)(?:resumeId|resume_id|简历ID|简历id)\\s*[:=：#-]?\\s*(\\d+)"
    );

    private static final Pattern JOB_ID_PATTERN = Pattern.compile(
            "(?i)(?:jobId|job_id|岗位ID|岗位id|职位ID|职位id)\\s*[:=：#-]?\\s*(\\d+)"
    );

    private static final Pattern APPLICATION_ID_PATTERN = Pattern.compile(
            "(?i)(?:applicationId|application_id|投递记录ID|投递记录id|求职记录ID|求职记录id|application)\\s*[:=：#-]?\\s*(\\d+)"
    );

    private static final Pattern MOCK_SESSION_ID_PATTERN = Pattern.compile(
            "(?i)(?:mockSessionId|mock_session_id|sessionId|session_id|模拟面试ID|模拟面试id)\\s*[:=：#-]?\\s*(\\d+)"
    );

    private static final Pattern MIN_SALARY_PATTERN = Pattern.compile(
            "(?i)(?:最低薪资|minSalary|薪资不低于|薪资至少|至少)\\s*[:=：]?\\s*(\\d{1,6})\\s*(k|K|千|元)?"
    );

    private static final Pattern QUOTED_RESUME_AND_JOB_PATTERN = Pattern.compile(
            "[「『“\"]([^」』”\"]*简历)[」』”\"].*?[「『“\"]([^」』”\"]+)[」』”\"]"
    );

    private static final Pattern RESUME_NAME_PATTERN = Pattern.compile(
            "(?:简历名称|简历名|简历)\\s*[:=：#-]?\\s*[「『“\"]?([^」』”\"，,。]+?简历)[」』”\"]?"
    );

    private static final Pattern JOB_TITLE_PATTERN = Pattern.compile(
            "(?:岗位名称|职位名称|岗位|职位)\\s*[:=：#-]?\\s*[「『“\"]?([^」』”\"，,。]+)[」』”\"]?"
    );

    private static final List<String> CITY_WORDS = List.of(
            "北京", "上海", "广州", "深圳", "杭州", "南京", "苏州", "成都", "武汉", "西安",
            "长沙", "郑州", "沈阳", "大连", "天津", "重庆", "厦门", "合肥", "青岛", "远程"
    );

    private static final List<String> KEYWORD_WORDS = List.of(
            "Java", "后端", "AI", "Agent", "RAG", "Spring Boot", "MySQL", "Redis",
            "前端", "Vue", "React", "TypeScript", "Python", "Go", "全栈", "实习"
    );

    /**
     * 从用户消息中抽取计划参数。
     *
     * 方法步骤:
     * 1. 先抽取兼容旧入口的数字 ID。
     * 2. 再抽取面向用户的新参数 resumeName/jobTitle。
     * 3. 最后抽取搜索类条件，例如城市、关键词和最低薪资。
     */
    public Map<String, Object> extract(String message) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (!StringUtils.hasText(message)) {
            return params;
        }

        putLongIfPresent(params, "resumeId", RESUME_ID_PATTERN, message);
        putLongIfPresent(params, "jobId", JOB_ID_PATTERN, message);
        putLongIfPresent(params, "applicationId", APPLICATION_ID_PATTERN, message);
        putLongIfPresent(params, "mockSessionId", MOCK_SESSION_ID_PATTERN, message);
        putNameParamsIfPresent(params, message);
        putSalaryIfPresent(params, message);
        putCityIfPresent(params, message);
        putKeywordIfPresent(params, message);
        return params;
    }

    private void putNameParamsIfPresent(Map<String, Object> params, String message) {
        /*
         * 优先解析成对引号表达。
         * 例如: 帮我分析「黄锋杰(后端)简历」和「Java 后端开发」是否匹配。
         */
        Matcher pairMatcher = QUOTED_RESUME_AND_JOB_PATTERN.matcher(message);
        if (pairMatcher.find()) {
            params.putIfAbsent("resumeName", pairMatcher.group(1).trim());
            params.putIfAbsent("jobTitle", pairMatcher.group(2).trim());
            return;
        }

        putStringIfPresent(params, "resumeName", RESUME_NAME_PATTERN, message);
        putStringIfPresent(params, "jobTitle", JOB_TITLE_PATTERN, message);
    }

    private void putLongIfPresent(Map<String, Object> params, String key, Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            params.put(key, Long.valueOf(matcher.group(1)));
        }
    }

    private void putStringIfPresent(Map<String, Object> params, String key, Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            params.put(key, matcher.group(1).trim());
        }
    }

    private void putSalaryIfPresent(Map<String, Object> params, String message) {
        Matcher matcher = MIN_SALARY_PATTERN.matcher(message);
        if (!matcher.find()) {
            return;
        }

        int value = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);
        if (StringUtils.hasText(unit) && List.of("k", "K", "千").contains(unit)) {
            value = value * 1000;
        }
        params.put("minSalary", value);
    }

    private void putCityIfPresent(Map<String, Object> params, String message) {
        for (String city : CITY_WORDS) {
            if (message.contains(city)) {
                params.put("city", city);
                return;
            }
        }
    }

    private void putKeywordIfPresent(Map<String, Object> params, String message) {
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        for (String keyword : KEYWORD_WORDS) {
            if (lowerMessage.contains(keyword.toLowerCase(Locale.ROOT))) {
                params.put("keyword", keyword);
                return;
            }
        }
    }
}
