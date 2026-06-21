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
 * 对各种类型的敏感数据进行自动脱敏处理，保护用户隐私
 * 日期:2026/6/21
 */
@Component
public class PiiMasker {

    //中国大陆手机号码正则表达式
    private static final Pattern CN_MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");

    //电子邮箱地址正则表达式
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-]{1,3})[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    //中国大陆居民身份证号码正则表达式
    private static final Pattern CN_ID_CARD = Pattern.compile("(?<![0-9Xx])(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?![0-9Xx])");

    //Token类敏感信息正则表达式
    private static final Pattern TOKEN_LIKE = Pattern.compile("(?i)(token|api[_-]?key|secret|password|passwd|pwd)([\"'\\s:=]+)([^\\s,\"'}]{4,})");

    /***
     * 脱敏任意类型的对象
     *
     * @param value 待脱敏的任意对象
     * @return 脱敏后的新对象，原始对象不会被修改
     */
    public Object maskObject(Object value) {
        if (value == null) {
            return null;
        }

        // 字符串类型：进行正则脱敏
        if (value instanceof CharSequence text) {
            return maskText(String.valueOf(text));
        }

        // 基本类型包装类和枚举：原样保留
        // 设计决策：避免脱敏后破坏日志中的统计字段（如状态码、耗时、数量等）
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value;
        }

        // Map类型：按键递归脱敏，敏感键直接隐藏值
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());

                // 如果是敏感键，直接替换为***，不再递归处理值
                // 设计决策：避免敏感值嵌套在复杂对象中漏脱敏
                masked.put(key, isSensitiveKey(key) ? "***" : maskObject(entry.getValue()));
            }
            return masked;
        }

        // 可迭代集合类型：逐项递归脱敏
        if (value instanceof Iterable<?> iterable) {
            List<Object> masked = new ArrayList<>();
            for (Object item : iterable) {
                masked.add(maskObject(item));
            }
            return masked;
        }

        // 数组类型：逐项递归脱敏
        if (value.getClass().isArray()) {
            List<Object> masked = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                masked.add(maskObject(Array.get(value, i)));
            }
            return masked;
        }

        // 其他未知类型：原样返回
        // 设计决策：避免破坏自定义对象的结构，防止出现序列化异常
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

        // 按顺序应用所有脱敏规则
        String masked = CN_MOBILE.matcher(text).replaceAll("$1****$2");
        masked = EMAIL.matcher(masked).replaceAll("$1***$2");
        masked = CN_ID_CARD.matcher(masked).replaceAll("$1********$2");
        masked = TOKEN_LIKE.matcher(masked).replaceAll("$1$2***");
        return masked;
    }

    /***
     * 判断Map的键是否为敏感键
     *
     * @param key Map的键名
     * @return true表示是敏感键，false表示是非敏感键
     */
    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }

        // 转换为小写进行不区分大小写的匹配
        String lower = key.toLowerCase();

        // 检查是否包含任意敏感关键词
        return lower.contains("password")
                || lower.contains("passwd")
                || lower.equals("pwd")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("apikey")
                || lower.contains("api_key");
    }
}
