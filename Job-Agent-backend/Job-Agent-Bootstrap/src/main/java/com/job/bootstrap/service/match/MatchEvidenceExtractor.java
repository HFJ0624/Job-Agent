package com.job.bootstrap.service.match;

import com.job.common.entity.position.JobPosition;
import com.job.common.vo.match.MatchEvidenceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能: 匹配证据提取器（任务 J5）。
 *
 * 解决什么问题:
 * 1. 原解释是模板句（“匹配度较高”），用户无法核对——不可核实的解释等于没有解释；
 * 2. 本组件把“已匹配技能/基础条件”落成“要求 ↔ 简历原文片段”的证据对，
 *    片段从简历原文截取（上下文窗口），忠于原文、可逐条核对。
 *
 * 设计说明:
 * 1. 证据窗口：命中词前后各 30 字符，尽量在句子边界截断，超长加省略号；
 * 2. 只提取不推断：找不到原文的技能不进证据列（进 missingSkills），
 *    绝不允许“模型觉得他会”变成证据。
 */
@Slf4j
@Component
public class MatchEvidenceExtractor {

    /** 证据上下文窗口半径（字符）。 */
    private static final int CONTEXT_RADIUS = 30;

    /** 证据片段最大长度。 */
    private static final int MAX_EVIDENCE_LENGTH = 120;

    /**
     * 提取已匹配技能的证据（每个技能一条，evidence 为简历原文片段）。
     *
     * @param resumeText    简历原文（已归一化）
     * @param matchedSkills 已匹配技能列表
     * @return 技能证据列表
     */
    public List<MatchEvidenceVO> skillEvidences(String resumeText, List<String> matchedSkills) {
        List<MatchEvidenceVO> evidences = new ArrayList<>();
        if (!StringUtils.hasText(resumeText) || matchedSkills == null || matchedSkills.isEmpty()) {
            return evidences;
        }

        String lowerResume = resumeText.toLowerCase();
        for (String skill : matchedSkills) {
            if (!StringUtils.hasText(skill)) {
                continue;
            }
            int index = lowerResume.indexOf(skill.toLowerCase());
            if (index < 0) {
                continue; // 无原文依据不进证据列
            }
            evidences.add(new MatchEvidenceVO(
                    "SKILL",
                    skill,
                    extractWindow(resumeText, index, skill.length()),
                    true
            ));
        }
        return evidences;
    }

    /**
     * 提取基础条件证据（学历/经验）：JD 要求 vs 简历中的对应表述。
     *
     * @param resumeText 简历原文
     * @param job        岗位信息
     * @return 条件证据列表（学历要求无原文佐证时 matched=false）
     */
    public List<MatchEvidenceVO> conditionEvidences(String resumeText, JobPosition job) {
        List<MatchEvidenceVO> evidences = new ArrayList<>();
        if (job == null) {
            return evidences;
        }

        // 学历证据：岗位要求 ↔ 简历中的学历表述
        if (StringUtils.hasText(job.getEducationReq())) {
            int index = findFirstKeyword(resumeText, List.of("本科", "硕士", "博士", "大专", "专科", "学士", "研究生"));
            evidences.add(new MatchEvidenceVO(
                    "CONDITION",
                    "学历要求：" + job.getEducationReq(),
                    index >= 0 ? extractWindow(resumeText, index, 2) : null,
                    index >= 0
            ));
        }

        // 经验证据：岗位要求 ↔ 简历中的经验/公司表述
        if (StringUtils.hasText(job.getExperienceReq())) {
            int index = findFirstKeyword(resumeText, List.of("年经验", "工作经验", "工作经历", "项目经验", "实习"));
            evidences.add(new MatchEvidenceVO(
                    "CONDITION",
                    "经验要求：" + job.getExperienceReq(),
                    index >= 0 ? extractWindow(resumeText, index, 3) : null,
                    index >= 0
            ));
        }
        return evidences;
    }

    private int findFirstKeyword(String text, List<String> keywords) {
        if (!StringUtils.hasText(text)) {
            return -1;
        }
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    /*
     * 上下文窗口截取：以命中位置为中心取前后各 CONTEXT_RADIUS 字符，
     * 尝试把边界挪到最近的句子标点，超长截断加省略号。
     */
    private String extractWindow(String text, int matchIndex, int matchLength) {
        int start = Math.max(0, matchIndex - CONTEXT_RADIUS);
        int end = Math.min(text.length(), matchIndex + matchLength + CONTEXT_RADIUS);

        // 边界对齐到句子标点（找不到就保持原边界）
        int sentenceStart = lastIndexOfAny(text, start, matchIndex, "。", "；", "\n", "，");
        if (sentenceStart > start) {
            start = sentenceStart + 1;
        }
        int sentenceEnd = indexOfAny(text, end, Math.min(text.length(), end + CONTEXT_RADIUS), "。", "；", "\n", "，");
        if (sentenceEnd > 0 && sentenceEnd < end + CONTEXT_RADIUS) {
            end = Math.min(text.length(), sentenceEnd + 1);
        }

        String window = text.substring(start, end).trim();
        if (start > 0) {
            window = "…" + window;
        }
        if (end < text.length()) {
            window = window + "…";
        }
        if (window.length() > MAX_EVIDENCE_LENGTH) {
            window = window.substring(0, MAX_EVIDENCE_LENGTH) + "…";
        }
        return window;
    }

    private int lastIndexOfAny(String text, int from, int to, String... separators) {
        int result = from;
        for (int i = Math.max(0, to - 1); i >= from; i--) {
            for (String separator : separators) {
                if (text.startsWith(separator, i)) {
                    return i;
                }
            }
        }
        return result;
    }

    private int indexOfAny(String text, int from, int to, String... separators) {
        for (int i = Math.max(0, from); i < to; i++) {
            for (String separator : separators) {
                if (text.startsWith(separator, i)) {
                    return i;
                }
            }
        }
        return -1;
    }
}
