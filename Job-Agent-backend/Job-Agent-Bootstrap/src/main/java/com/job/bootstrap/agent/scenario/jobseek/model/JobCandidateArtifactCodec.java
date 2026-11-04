package com.job.bootstrap.agent.scenario.jobseek.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.domain.job.JobCandidates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 候选产物编解码（审查 F4 定点修复）。
 *
 * 背景: 保存端写的是 [{jobId, raw:{...岗位字段...}}]，而读取端曾把外层对象当 raw，
 * 导致 city/salary 等字段全部丢失、重试时硬条件复核失败。这里集中编解码，
 * 既修 bug 也便于单测覆盖真实结构。
 */
public final class JobCandidateArtifactCodec {

    private JobCandidateArtifactCodec() {
    }

    /**
     * 解码产物 JSON（兼容两种格式：{jobId, raw:{...}} 与平铺字段）。
     */
    public static List<JobCandidates.JobCandidateItem> decode(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<JobCandidates.JobCandidateItem> items = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                Object rawField = item.get("raw");
                Map<String, Object> jobFields = rawField instanceof Map<?, ?> nested
                        ? castMap(nested)
                        : item;
                Object jobId = item.containsKey("jobId") ? item.get("jobId") : jobFields.get("jobId");
                if (jobId == null) {
                    jobId = jobFields.get("id");
                }
                Long id = jobId instanceof Number number ? number.longValue() : null;
                if (id != null) {
                    items.add(new JobCandidates.JobCandidateItem(id, jobFields));
                }
            }
            return items;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
