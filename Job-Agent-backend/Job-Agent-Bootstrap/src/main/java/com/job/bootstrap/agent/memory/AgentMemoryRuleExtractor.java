package com.job.bootstrap.agent.memory;

import com.job.enums.AgentMemoryType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 作者: hfj
 * 功能: 基于规则的长期记忆抽取器
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 规则抽取只处理高确定性表达，例如“以后你叫xxx”“我喜欢北京 Java 后端”。
 * 2. 这些事实不需要再调用大模型，能降低成本，并且让用户刚说完的称呼下一轮立刻生效。
 * 3. 复杂、隐含、跨句总结类记忆交给可选 LLM 抽取器处理。
 */
@Component
public class AgentMemoryRuleExtractor {

    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.95");
    private static final BigDecimal NORMAL_CONFIDENCE = new BigDecimal("0.85");
    private static final BigDecimal HIGH_IMPORTANCE = new BigDecimal("0.85");
    private static final BigDecimal NORMAL_IMPORTANCE = new BigDecimal("0.65");

    private static final Pattern ASSISTANT_NICKNAME = Pattern.compile(
            "(?:以后|之后|以后都|以后请|刚才说)?(?:你|助手|AI助手)(?:的)?(?:名字|名称)?(?:叫|叫做|称为)([^，。！？；\\n]{2,30})"
    );

    private static final Pattern USER_NAME = Pattern.compile(
            "(?:我叫|我的名字叫|你可以叫我|以后叫我)([^，。！？；\\n]{1,20})"
    );

    private static final Pattern MIN_SALARY = Pattern.compile(
            "(?:最低薪资|薪资不低于|薪资至少|至少|最低|期望薪资|希望薪资)\\D{0,8}(\\d{1,6})\\s*(k|K|千|万)?"
    );

    private static final List<String> CITY_WORDS = List.of(
            "北京", "上海", "广州", "深圳", "杭州", "南京", "苏州", "成都", "武汉", "西安",
            "长沙", "郑州", "沈阳", "大连", "天津", "重庆", "厦门", "合肥", "青岛", "远程"
    );

    private static final List<String> ROLE_WORDS = List.of(
            "Java 后端", "Java后端", "后端", "前端", "AI", "算法", "Agent", "RAG",
            "产品经理", "测试", "运维", "全栈", "Python", "Go"
    );

    private static final List<String> ANSWER_STYLE_WORDS = List.of(
            "简洁", "详细", "正式", "自然", "口语化", "专业", "直接", "真诚"
    );

    /**
     * 从用户输入中抽取候选记忆。
     *
     * 方法步骤:
     * 1. 如果用户明确说“不要记住”，整轮跳过，尊重用户控制权。
     * 2. 先抽取称呼类强事实，因为这类事实最影响用户体感。
     * 3. 再抽取求职偏好、薪资、回答风格和排除项。
     * 4. 所有候选记忆只返回给上层，不在这里直接入库。
     *
     * @param message 已脱敏的用户输入
     * @return 候选记忆列表
     */
    public List<AgentMemoryCandidate> extract(String message) {
        if (!StringUtils.hasText(message) || containsDoNotRemember(message)) {
            return List.of();
        }

        List<AgentMemoryCandidate> candidates = new ArrayList<>();
        rememberAssistantNickname(candidates, message);
        rememberUserName(candidates, message);
        rememberCityPreference(candidates, message);
        rememberRolePreference(candidates, message);
        rememberMinSalary(candidates, message);
        rememberAnswerStyle(candidates, message);
        rememberExcludedPreference(candidates, message);
        return candidates;
    }

    private void rememberAssistantNickname(List<AgentMemoryCandidate> candidates, String message) {
        Matcher matcher = ASSISTANT_NICKNAME.matcher(message);
        if (!matcher.find()) {
            return;
        }

        String nickname = cleanValue(matcher.group(1), 30);
        if (!StringUtils.hasText(nickname)) {
            return;
        }

        candidates.add(candidate(
                AgentMemoryType.COMMUNICATION_STYLE,
                "assistant_nickname",
                nickname,
                "用户希望助手称为: " + nickname,
                HIGH_CONFIDENCE,
                HIGH_IMPORTANCE
        ));
    }

    private void rememberUserName(List<AgentMemoryCandidate> candidates, String message) {
        Matcher matcher = USER_NAME.matcher(message);
        if (!matcher.find()) {
            return;
        }

        String name = cleanValue(matcher.group(1), 20);
        if (!StringUtils.hasText(name)) {
            return;
        }

        candidates.add(candidate(
                AgentMemoryType.USER_PREFERENCE,
                "user_name",
                name,
                "用户自称: " + name,
                HIGH_CONFIDENCE,
                NORMAL_IMPORTANCE
        ));
    }

    private void rememberCityPreference(List<AgentMemoryCandidate> candidates, String message) {
        if (!containsAny(message, List.of("找", "岗位", "职位", "工作", "求职", "喜欢", "偏好", "想去"))) {
            return;
        }

        for (String city : CITY_WORDS) {
            if (message.contains(city)) {
                candidates.add(candidate(
                        AgentMemoryType.USER_PREFERENCE,
                        "preferred_city",
                        city,
                        "用户偏好的求职城市: " + city,
                        NORMAL_CONFIDENCE,
                        HIGH_IMPORTANCE
                ));
                return;
            }
        }
    }

    private void rememberRolePreference(List<AgentMemoryCandidate> candidates, String message) {
        if (!containsAny(message, List.of("岗位", "职位", "方向", "工作", "求职", "喜欢", "想找"))) {
            return;
        }

        String lowerMessage = message.toLowerCase(Locale.ROOT);
        for (String role : ROLE_WORDS) {
            if (lowerMessage.contains(role.toLowerCase(Locale.ROOT))) {
                String normalizedRole = "Java后端".equals(role) ? "Java 后端" : role;
                candidates.add(candidate(
                        AgentMemoryType.USER_PREFERENCE,
                        "target_role",
                        normalizedRole,
                        "用户关注的岗位方向: " + normalizedRole,
                        NORMAL_CONFIDENCE,
                        HIGH_IMPORTANCE
                ));
                return;
            }
        }
    }

    private void rememberMinSalary(List<AgentMemoryCandidate> candidates, String message) {
        Matcher matcher = MIN_SALARY.matcher(message);
        if (!matcher.find()) {
            return;
        }

        String salary = matcher.group(1);
        String unit = matcher.group(2);
        String value = StringUtils.hasText(unit) ? salary + unit.toLowerCase(Locale.ROOT) : salary;
        candidates.add(candidate(
                AgentMemoryType.USER_PREFERENCE,
                "min_salary",
                value,
                "用户期望的最低薪资: " + value,
                NORMAL_CONFIDENCE,
                NORMAL_IMPORTANCE
        ));
    }

    private void rememberAnswerStyle(List<AgentMemoryCandidate> candidates, String message) {
        if (!containsAny(message, List.of("回答", "回复", "说话", "风格", "表达"))) {
            return;
        }

        for (String style : ANSWER_STYLE_WORDS) {
            if (message.contains(style)) {
                candidates.add(candidate(
                        AgentMemoryType.COMMUNICATION_STYLE,
                        "answer_style",
                        style,
                        "用户偏好的回答风格: " + style,
                        NORMAL_CONFIDENCE,
                        NORMAL_IMPORTANCE
                ));
                return;
            }
        }
    }

    private void rememberExcludedPreference(List<AgentMemoryCandidate> candidates, String message) {
        if (!containsAny(message, List.of("不想去", "不要推荐", "排除", "不接受", "不考虑"))) {
            return;
        }

        if (message.contains("外包")) {
            addExcluded(candidates, "excluded_outsourcing", "外包公司");
        }
        if (message.contains("996")) {
            addExcluded(candidates, "excluded_996", "996 工作制");
        }
        if (message.contains("大小周")) {
            addExcluded(candidates, "excluded_big_small_week", "大小周");
        }
    }

    private void addExcluded(List<AgentMemoryCandidate> candidates, String key, String value) {
        candidates.add(candidate(
                AgentMemoryType.USER_PREFERENCE,
                key,
                value,
                "用户明确排除: " + value,
                HIGH_CONFIDENCE,
                HIGH_IMPORTANCE
        ));
    }

    private AgentMemoryCandidate candidate(
            AgentMemoryType type,
            String key,
            String value,
            String summary,
            BigDecimal confidence,
            BigDecimal importance
    ) {
        return AgentMemoryCandidate.builder()
                .memoryType(type)
                .memoryKey(key)
                .memoryValue(value)
                .summary(summary)
                .confidence(confidence)
                .importance(importance)
                .build();
    }

    private boolean containsDoNotRemember(String message) {
        return containsAny(message, List.of("不要记住", "别记住", "不用记住", "不要保存", "别保存"));
    }

    private boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String cleanValue(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String cleaned = value.trim()
                .replaceAll("^(叫|叫做|称为|是)", "")
                .replaceAll("[\"'“”‘’]", "")
                .trim();
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned;
    }
}
