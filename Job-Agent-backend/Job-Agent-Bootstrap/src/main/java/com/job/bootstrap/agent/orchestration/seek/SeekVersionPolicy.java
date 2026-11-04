package com.job.bootstrap.agent.orchestration.seek;

import com.job.bootstrap.agent.domain.job.SearchConstraints;
import com.job.common.entity.resume.JobResume;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 功能: 复合任务产物版本策略（从 JobSeekWorkflow 抽出）。
 *
 * <p>为什么独立：版本决定"能不能复用产物"，是重试正确性的核心。
 * 规则集中在一处，避免保存端与查找端算法不一致（历史上踩过"复用永远不命中"）。</p>
 *
 * <ul>
 *   <li>画像版本 = 简历版本 + 用户显式方向（空表示未指定）</li>
 *   <li>候选版本 = 条件快照（方向/城市/薪资/学历/召回词）</li>
 *   <li>报告版本 = 候选版本 + 画像/候选产物 ID</li>
 * </ul>
 */
public final class SeekVersionPolicy {

    private SeekVersionPolicy() {
    }

    public static String resumeVersion(JobResume resume) {
        String seed = resume.getId() + ":" + (resume.getUpdateTime() == null ? ""
                : resume.getUpdateTime().getTime()) + ":" + hash(resume.getRawText());
        return hash(seed);
    }

    /** 画像版本：保存与复用必须用同一入参，否则永远命中不了。 */
    public static String profileVersion(JobResume resume, String targetRoleHint) {
        return hash(resumeVersion(resume) + "|" + (targetRoleHint == null ? "" : targetRoleHint));
    }

    public static String constraintVersion(SearchConstraints constraints, Long resumeId) {
        return hash(resumeId + "|" + constraints.targetRole() + "|" + constraints.city() + "|"
                + constraints.minSalary() + "|" + constraints.educationReq() + "|" + constraints.keyword());
    }

    /** 报告版本：上下游产物变化则旧报告失效。 */
    public static String reportVersion(String constraintVersion, Long profileArtifactId, Long candidatesArtifactId) {
        return hash(constraintVersion + "|" + profileArtifactId + "|" + candidatesArtifactId);
    }

    private static String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Exception exception) {
            return String.valueOf(text == null ? 0 : text.hashCode());
        }
    }
}
