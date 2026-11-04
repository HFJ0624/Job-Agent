package com.job.bootstrap.agent.scenario.jobseek.assembler;

import com.job.bootstrap.agent.scenario.jobsearch.JobCandidates;
import com.job.bootstrap.agent.scenario.match.MatchReport;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 复合任务对外答复组装（从 JobSeekWorkflow 抽出）。
 *
 * <p>边界：只接受**已通过校验**的候选与报告，负责排版与展示格式
 * （公司缺失显示"公司未知"、薪资元/月 → K/月），不做业务判断。
 * 改文案或薪资格式时不必进入 Workflow 编排代码。</p>
 */
public final class SeekAnswerAssembler {

    private SeekAnswerAssembler() {
    }

    public static String assemble(List<JobCandidates.JobCandidateItem> candidates, MatchReport report) {
        Map<Long, Map<String, Object>> rawById = new LinkedHashMap<>();
        candidates.forEach(item -> rawById.put(item.jobId(), item.raw()));

        StringBuilder builder = new StringBuilder();
        builder.append("结合你的简历，我从 ").append(candidates.size()).append(" 个候选里挑出 ")
                .append(report.rankedItems().size()).append(" 个岗位：\n\n");

        int index = 1;
        for (MatchReport.RankedItem item : report.rankedItems()) {
            Map<String, Object> raw = rawById.get(item.jobId());
            if (raw == null) {
                continue; // 只输出可信候选
            }
            builder.append(index++).append(". ")
                    .append(text(raw.get("jobTitle"))).append("｜")
                    .append(companyOf(raw)).append("｜")
                    .append(text(raw.get("city")));
            String salary = salaryOf(raw.get("minSalary"), raw.get("maxSalary"));
            if (StringUtils.hasText(salary)) {
                builder.append("｜").append(salary);
            }
            builder.append("\n");
            if (!item.matchPoints().isEmpty()) {
                builder.append("   匹配点：").append(String.join("；", limit(item.matchPoints(), 3))).append("\n");
            }
            if (!item.gaps().isEmpty()) {
                builder.append("   待补能力（简历未见证据）：").append(String.join("；", limit(item.gaps(), 3))).append("\n");
            }
            if (!item.suggestions().isEmpty()) {
                builder.append("   建议：").append(String.join("；", limit(item.suggestions(), 2))).append("\n");
            }
        }

        if (report.completedCount() < report.totalCandidates()) {
            builder.append("\n说明：候选共 ").append(report.totalCandidates())
                    .append(" 个，本次完整比较 ").append(report.completedCount()).append(" 个");
            if (!report.uncertainties().isEmpty()) {
                builder.append("；").append(String.join("；", limit(report.uncertainties(), 2)));
            }
            builder.append("。\n");
        }
        builder.append("\n如果你愿意，我可以针对排名第一的岗位给出更细的面试准备计划。");
        return builder.toString();
    }

    private static String companyOf(Map<String, Object> raw) {
        String company = text(raw.get("companyName"));
        return StringUtils.hasText(company) ? company : "公司未知";
    }

    // 元/月 → K/月
    private static String salaryOf(Object minValue, Object maxValue) {
        Integer min = integerOf(minValue);
        Integer max = integerOf(maxValue);
        if (min == null && max == null) {
            return null;
        }
        String left = min == null ? "?" : String.valueOf(Math.round(min / 1000.0));
        String right = max == null ? "?" : String.valueOf(Math.round(max / 1000.0));
        return left + "-" + right + "K/月";
    }

    private static List<String> limit(List<String> values, int max) {
        return values.size() > max ? values.subList(0, max) : values;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Integer integerOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
