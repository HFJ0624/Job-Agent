package com.job.bootstrap.agent.scenario.match;

import java.math.BigDecimal;
import java.util.List;

/**
 * 功能: 匹配报告产物（第二阶段 MatchAgent 输出）。
 *
 * 设计说明:
 * 1. ruleScore 与模型的定性判断**分别存储**：ruleScore 来自规则计算（可信），
 *    matchPoints/gaps/suggestions 是模型解释，不伪装成客观分数；
 * 2. 每个排名项必须引用当前有效候选里的 jobId，evidenceRefs 指向简历段落/JD 字段；
 * 3. 缺口只写“岗位要求但简历未见证据”，不断言用户不会。
 *
 * @param resumeProfileArtifactId  简历画像产物ID
 * @param jobCandidatesArtifactId  候选产物ID
 * @param constraintVersion        条件版本（变化即报告失效）
 * @param rankedItems              排序项
 * @param uncertainties            不确定项
 * @param completedCount           实际完成比较的数量
 * @param totalCandidates          候选总数
 * @param outcome                  COMPLETED / PARTIAL
 */
public record MatchReport(
        Long resumeProfileArtifactId,
        Long jobCandidatesArtifactId,
        String constraintVersion,
        List<RankedItem> rankedItems,
        List<String> uncertainties,
        int completedCount,
        int totalCandidates,
        String outcome
) {

    /**
     * 单个排名项。
     *
     * @param jobId       候选岗位ID（必须来自当前有效候选）
     * @param rank        排名（1..n 连续）
     * @param ruleScore   规则分（可空；与模型判断分开）
     * @param matchPoints 匹配点（带证据引用）
     * @param gaps        缺口（简历未见证据的岗位要求）
     * @param suggestions 准备建议
     * @param evidenceRefs 证据引用（sectionId 或 JD 字段名）
     */
    public record RankedItem(
            Long jobId,
            int rank,
            BigDecimal ruleScore,
            List<String> matchPoints,
            List<String> gaps,
            List<String> suggestions,
            List<String> evidenceRefs
    ) {
    }
}
