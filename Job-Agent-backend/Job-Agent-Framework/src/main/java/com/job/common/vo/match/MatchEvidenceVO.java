package com.job.common.vo.match;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能: 匹配证据（任务 J5）。
 *
 * 设计说明:
 * 1. 每条证据 = 一个可核对的“要求 ↔ 原文”对：requirement 来自 JD 或用户偏好，
 *    evidence 是简历原文中的实际片段——解释忠于原文，不补造；
 * 2. type 区分证据来源：SKILL（技能命中）、CONDITION（学历/经验等基础条件）；
 * 3. matched=false 表示这是“缺口证据”（岗位要求但简历中没有）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchEvidenceVO {

    /** 证据类型：SKILL / CONDITION。 */
    private String type;

    /** 要求描述（JD 要求或用户偏好，如 "Java"、"本科及以上"）。 */
    private String requirement;

    /** 简历原文中的证据片段（上下文截取，缺失时为 null）。 */
    private String evidence;

    /** 是否命中：true=有证据支撑，false=缺口。 */
    private Boolean matched;
}
