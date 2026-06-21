package com.job.bootstrap.agent.guardrail;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 作者:hfj
 * 功能:PII 脱敏工具
 * 日期:2026/6/21
 */
@Component
public class PiiMasker {

    private static final Pattern CN_MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-]{1,3})[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern CN_ID_CARD = Pattern.compile("(?<![0-9Xx])(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?![0-9Xx])");
    private static final Pattern TOKEN_LIKE = Pattern.compile("(?i)(token|api[_-]?key|secret|password|passwd|pwd)([\"'\\s:=]+)([^\\s,\"'}]{4,})");

    /**
     * 脱敏任意对象。
     *
     * 方法步骤:
     * 1. String 直接走正则脱敏。
     * 2. Map 按 key 递归脱敏；如果 key 本身是 password/token/secret，直接整值替换。
     * 3. List 和数组逐项递归脱敏。
     * 4. 数字、布尔值原样保留，避免破坏 Trace 的统计字段。
     */
    public Object maskObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence text) {
            return maskText(String.valueOf(text));
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                masked.put(key, isSensitiveKey(key) ? "***" : maskObject(entry.getValue()));
            }
            return masked;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> masked = new ArrayList<>();
            for (Object item : iterable) {
                masked.add(maskObject(item));
            }
            return masked;
        }
        if (value.getClass().isArray()) {
            List<Object> masked = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                masked.add(maskObject(Array.get(value, i)));
            }
            return masked;
        }
        return value;
    }

    /**
     * 脱敏文本。
     *
     * 方法步骤:
     * 1. 手机号保留前三后四。
     * 2. 邮箱保留前 1 到 3 位和域名。
     * 3. 身份证保留前六后三。
     * 4. token/password/apiKey 这类键值对直接隐藏值。
     */
    public String maskText(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        String masked = CN_MOBILE.matcher(text).replaceAll("$1****$2");
        masked = EMAIL.matcher(masked).replaceAll("$1***$2");
        masked = CN_ID_CARD.matcher(masked).replaceAll("$1********$2");
        masked = TOKEN_LIKE.matcher(masked).replaceAll("$1$2***");
        return masked;
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String lower = key.toLowerCase();
        return lower.contains("password")
                || lower.contains("passwd")
                || lower.equals("pwd")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("apikey")
                || lower.contains("api_key");
    }
}
