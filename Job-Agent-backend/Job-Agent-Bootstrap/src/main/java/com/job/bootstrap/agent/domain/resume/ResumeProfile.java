package com.job.bootstrap.agent.domain.resume;

import java.util.List;

/**
 * 功能: 简历画像产物（第二阶段 ResumeAgent 输出）。
 *
 * 设计说明:
 * 1. 事实与建议分开：skills/experiences/evidence 必须来自简历原文；
 *    targetRole 若为推断则标记 inferred=true；
 * 2. evidence 每条含 fact + quote（原文片段）——节点校验 quote 必须能在简历原文中找到，
 *    从机制上禁止编造项目/技术/量化指标；
 * 3. “简历没有证据”与“用户不会某技能”是两件事：缺口只写 missingInformation/uncertainties，
 *    不下“不会”的结论。
 *
 * @param resumeId          简历ID
 * @param resumeVersion     简历版本（parse 后的更新时间/哈希，变更即画像失效）
 * @param resumeName        简历名称
 * @param targetRole        目标方向（可空）
 * @param targetRoleInferred 目标方向是否模型推断
 * @param skills            技能要点
 * @param experiences       项目/实习经历要点
 * @param evidence          证据（事实 ↔ 原文片段）
 * @param missingInformation 简历中缺失的信息（如缺少量化指标）
 * @param uncertainties     不确定项（如方向不明确）
 * @param outcome           COMPLETED / NEED_INPUT / PARTIAL
 * @param question          NEED_INPUT 时的具体问题
 */
public record ResumeProfile(
        Long resumeId,
        String resumeVersion,
        String resumeName,
        String targetRole,
        boolean targetRoleInferred,
        List<String> skills,
        List<String> experiences,
        List<Evidence> evidence,
        List<String> missingInformation,
        List<String> uncertainties,
        String outcome,
        String question
) {

    /**
     * 一条证据。
     *
     * @param fact       事实描述
     * @param sectionRef 段落引用（S1..Sn，可为空）
     * @param quote      简历原文片段
     */
    public record Evidence(String fact, String sectionRef, String quote) {
    }
}
