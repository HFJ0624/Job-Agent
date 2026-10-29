package com.job.bootstrap.service.match;

import com.job.common.entity.position.JobPosition;
import com.job.common.vo.match.MatchEvidenceVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MatchEvidenceExtractor 单元测试（任务 J5）。
 *
 * 覆盖重点:
 * 1. 已匹配技能提取原文证据（上下文窗口）；
 * 2. 无原文依据的技能不进证据列（绝不编造）；
 * 3. 条件证据（学历/经验）：有原文 matched=true，无原文 matched=false 且 evidence 为 null。
 */
class MatchEvidenceExtractorTest {

    private final MatchEvidenceExtractor extractor = new MatchEvidenceExtractor();

    private static final String RESUME = "张三，本科，3年Java后端开发经验。熟悉Spring Boot、MySQL、Redis。"
            + "在电商系统中负责订单模块，接口优化后响应时间降低50%，QPS 3000。";

    @Test
    void matchedSkillShouldCarryResumeEvidence() {
        List<MatchEvidenceVO> evidences = extractor.skillEvidences(RESUME, List.of("Spring Boot", "Redis"));

        assertThat(evidences).hasSize(2);
        MatchEvidenceVO spring = evidences.stream()
                .filter(e -> "Spring Boot".equals(e.getRequirement())).findFirst().orElseThrow();
        assertThat(spring.getType()).isEqualTo("SKILL");
        assertThat(spring.getMatched()).isTrue();
        // 证据必须是简历原文的真实片段
        assertThat(spring.getEvidence()).contains("Spring Boot");
        assertThat(RESUME.replace("…", "")).contains(
                spring.getEvidence().replace("…", "").replace("…", ""));
    }

    @Test
    void skillWithoutResumeTextShouldNotProduceEvidence() {
        List<MatchEvidenceVO> evidences = extractor.skillEvidences(RESUME, List.of("Kubernetes"));

        assertThat(evidences).isEmpty();
    }

    @Test
    void conditionEvidenceShouldMarkMissingWhenNoResumeProof() {
        JobPosition job = new JobPosition();
        job.setEducationReq("硕士");
        job.setExperienceReq("5年");

        List<MatchEvidenceVO> evidences = extractor.conditionEvidences(RESUME, job);

        // 学历：简历有“本科”→ 命中但等级不足由评分处理，证据存在
        MatchEvidenceVO education = evidences.stream()
                .filter(e -> e.getRequirement().contains("学历")).findFirst().orElseThrow();
        assertThat(education.getMatched()).isTrue();
        assertThat(education.getEvidence()).contains("本科");
        // 经验：简历没有“5年/工作经验”字样 → 缺口证据
        MatchEvidenceVO experience = evidences.stream()
                .filter(e -> e.getRequirement().contains("经验")).findFirst().orElseThrow();
        assertThat(experience.getMatched()).isFalse();
        assertThat(experience.getEvidence()).isNull();
    }

    @Test
    void blankResumeShouldProduceNoEvidence() {
        assertThat(extractor.skillEvidences("  ", List.of("Java"))).isEmpty();
        assertThat(extractor.conditionEvidences("", null)).isEmpty();
    }
}
