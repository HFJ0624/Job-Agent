package com.job.bootstrap.agent.guardrail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PiiMasker 单元测试。
 *
 * 覆盖重点:
 * 1. 既有规则：手机号、邮箱、身份证、token/api_key/password 键值对脱敏。
 * 2. 新增盲区（任务12）：Bearer 令牌、accessKey、credential 脱敏。
 * 3. 空值/原样透传。
 */
class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    void mobileShouldBeMasked() {
        assertThat(masker.maskText("联系我 13800138000"))
                .contains("138****8000")
                .doesNotContain("13800138000");
    }

    @Test
    void emailShouldBeMasked() {
        assertThat(masker.maskText("邮箱 zhangsan@example.com"))
                .contains("***")
                .doesNotContain("zhangsan@");
    }

    @Test
    void idCardShouldBeMasked() {
        assertThat(masker.maskText("身份证 110101199003077123"))
                .contains("********")
                .doesNotContain("19900307");
    }

    @Test
    void tokenLikeKeyValueShouldBeMasked() {
        assertThat(masker.maskText("api_key: abcd1234efgh")).doesNotContain("abcd1234efgh");
        assertThat(masker.maskText("password=SuperSecret1")).doesNotContain("SuperSecret1");
    }

    @Test
    void bearerTokenShouldBeMasked() {
        assertThat(masker.maskText("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9"))
                .contains("Bearer ***")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    void accessKeyAndCredentialShouldBeMasked() {
        assertThat(masker.maskText("accessKey: AKIA1234567890XYZ")).doesNotContain("AKIA1234567890XYZ");
        assertThat(masker.maskText("credential: mycred12345")).doesNotContain("mycred12345");
    }

    @Test
    void sensitiveMapKeyShouldBeHidden() {
        java.util.Map<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("token", "abcd1234efgh");
        input.put("name", "zhangsan");
        Object masked = masker.maskObject(input);
        assertThat(masked.toString()).doesNotContain("abcd1234efgh");
    }

    @Test
    void blankOrNullShouldPassThrough() {
        assertThat(masker.maskText("")).isEmpty();
        assertThat(masker.maskText(null)).isNull();
    }
}
