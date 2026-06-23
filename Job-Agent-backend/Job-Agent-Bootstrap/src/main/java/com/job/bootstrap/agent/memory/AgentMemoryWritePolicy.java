package com.job.bootstrap.agent.memory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 作者: hfj
 * 功能: Agent 长期记忆写入策略
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 抽取器只负责“发现可能值得记的事实”，本类负责判断“能不能写入长期记忆库”。
 * 2. 这样做可以防止模型或规则把低价值、敏感、问题句、临时上下文误写成长期事实。
 */
@Component
public class AgentMemoryWritePolicy {

    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.60");
    private static final int MAX_VALUE_LENGTH = 1000;

    private static final Pattern CN_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CN_ID_CARD = Pattern.compile("(?<![0-9Xx])\\d{17}[0-9Xx](?![0-9Xx])");

    private static final List<String> QUESTION_WORDS = List.of("什么", "谁", "吗", "？", "?", "是不是", "能不能");
    private static final List<String> SENSITIVE_WORDS = List.of("密码", "口令", "token", "api key", "apikey", "secret", "身份证", "手机号");

    /**
     * 判断候选记忆是否允许入库。
     *
     * 方法步骤:
     * 1. 先校验基础字段，避免没有归属、没有 key 或没有正文的记录进入长期库。
     * 2. 再校验置信度，低置信度事实不自动沉淀，后续可进入人工审核队列。
     * 3. 最后过滤敏感信息和疑问句，避免把“用户问了什么”误当成“用户事实”。
     *
     * @param candidate 候选记忆
     * @return true 表示允许保存
     */
    public boolean allowWrite(AgentMemoryCandidate candidate) {
        if (candidate == null
                || candidate.getMemoryType() == null
                || !StringUtils.hasText(candidate.getMemoryKey())
                || !StringUtils.hasText(candidate.getMemoryValue())) {
            return false;
        }

        if (candidate.getConfidence() != null && candidate.getConfidence().compareTo(MIN_CONFIDENCE) < 0) {
            return false;
        }

        String value = candidate.getMemoryValue().trim();
        if (value.length() > MAX_VALUE_LENGTH) {
            return false;
        }

        return !containsSensitiveData(value) && !looksLikeQuestion(value);
    }

    private boolean containsSensitiveData(String value) {
        String lower = value.toLowerCase();
        if (CN_MOBILE.matcher(value).find()
                || EMAIL.matcher(value).find()
                || CN_ID_CARD.matcher(value).find()) {
            return true;
        }

        for (String word : SENSITIVE_WORDS) {
            if (lower.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeQuestion(String value) {
        for (String word : QUESTION_WORDS) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
