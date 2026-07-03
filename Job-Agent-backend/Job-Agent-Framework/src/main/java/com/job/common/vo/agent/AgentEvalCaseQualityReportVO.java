package com.job.common.vo.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能:Agent Eval 用例质量检查报告。
 *
 * 设计说明:
 * 1. 该报告用于运行回归前检查用例配置是否合理。
 * 2. 它只检查 Eval 用例本身，不调用模型，也不修改任何数据。
 * 3. Admin 页面可以根据 issueItems 引导管理员跳转编辑用例，减少无效失败。
 */
@Data
public class AgentEvalCaseQualityReportVO {
    private Integer totalCaseCount = 0;
    private Integer problemCaseCount = 0;
    private Integer highRiskIssueCount = 0;
    private Integer mediumRiskIssueCount = 0;
    private Integer lowRiskIssueCount = 0;
    private List<IssueItem> issues = new ArrayList<>();

    /**
     * 单条用例质量问题。
     *
     * 说明:
     * 1. riskLevel 用于排序和页面标色。
     * 2. issueType 是稳定问题编码，后续可以接快捷修复。
     * 3. suggestion 是管理员能直接执行的修复建议。
     */
    @Data
    public static class IssueItem {
        private Long caseId;
        private String caseName;
        private String evalType;
        private String riskLevel;
        private String issueType;
        private String issueMessage;
        private String suggestion;
        private Boolean fixable = false;
        private String fixActionType;
        private String fixButtonText;
        private String fixConfirmText;
    }
}
