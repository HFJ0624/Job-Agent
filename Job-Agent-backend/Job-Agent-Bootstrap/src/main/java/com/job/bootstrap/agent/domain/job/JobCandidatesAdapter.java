package com.job.bootstrap.agent.domain.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 岗位候选适配层（第一阶段）。
 *
 * 职责:
 * 1. 把 JobSearchTool（{total,page,pageSize,jobs:[...]}）与 JobRecommendTool（[...]）统一成 JobCandidates；
 * 2. 合并去重（按 jobId，保留首次出现顺序）；
 * 3. 分页/总数不可靠时置空，不编造。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobCandidatesAdapter {

    public static final String SOURCE_SEARCH = "job_search";
    public static final String SOURCE_RECOMMEND = "job_recommend";

    private final ObjectMapper objectMapper;

    /** 搜索工具输出解析。 */
    public JobCandidates fromSearchResult(String json) {
        JsonNode root = parse(json);
        if (root == null) {
            return JobCandidates.empty(SOURCE_SEARCH);
        }
        List<JobCandidates.JobCandidateItem> items = new ArrayList<>();
        JsonNode jobs = root.path("jobs");
        if (jobs.isArray()) {
            for (JsonNode job : jobs) {
                JobCandidates.JobCandidateItem item = toItem(job);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return new JobCandidates(
                dedupe(items),
                root.path("total").isNumber() ? root.path("total").asLong() : null,
                root.path("page").isNumber() ? root.path("page").asInt() : null,
                root.path("pageSize").isNumber() ? root.path("pageSize").asInt() : null,
                resolveHasMore(root.path("total"), root.path("page"), root.path("pageSize")),
                SOURCE_SEARCH,
                System.currentTimeMillis()
        );
    }

    /** 推荐工具输出解析（裸数组；总数/分页未知）。 */
    public JobCandidates fromRecommendResult(String json, Integer page, Integer pageSize) {
        JsonNode root = parse(json);
        if (root == null || !root.isArray()) {
            return JobCandidates.empty(SOURCE_RECOMMEND);
        }
        List<JobCandidates.JobCandidateItem> items = new ArrayList<>();
        for (JsonNode job : root) {
            JobCandidates.JobCandidateItem item = toItem(job);
            if (item != null) {
                items.add(item);
            }
        }
        return new JobCandidates(
                dedupe(items),
                null, // 推荐工具不返回总数，不编造
                page,
                pageSize,
                null, // 无法判断是否还有更多
                SOURCE_RECOMMEND,
                System.currentTimeMillis()
        );
    }

    /** 合并两批候选（去重保序）。 */
    public JobCandidates merge(JobCandidates first, JobCandidates second) {
        if (first == null || first.isEmpty()) {
            return second == null ? JobCandidates.empty("merged") : second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        List<JobCandidates.JobCandidateItem> merged = new ArrayList<>(first.items());
        merged.addAll(second.items());
        return new JobCandidates(
                dedupe(merged),
                null,
                first.page(),
                first.pageSize(),
                first.hasMore(),
                first.source() + "+" + second.source(),
                System.currentTimeMillis()
        );
    }

    private JobCandidates.JobCandidateItem toItem(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        // 搜索工具用 jobId，推荐 VO 可能用 id —— 两者都认，认不出就丢弃（不猜）
        Long jobId = null;
        if (node.path("jobId").canConvertToLong()) {
            jobId = node.path("jobId").asLong();
        } else if (node.path("id").canConvertToLong()) {
            jobId = node.path("id").asLong();
        }
        if (jobId == null || jobId <= 0) {
            return null;
        }
        Map<String, Object> raw = objectMapper.convertValue(node, new LinkedHashMap<String, Object>().getClass());
        return new JobCandidates.JobCandidateItem(jobId, raw);
    }

    private List<JobCandidates.JobCandidateItem> dedupe(List<JobCandidates.JobCandidateItem> items) {
        Map<Long, JobCandidates.JobCandidateItem> unique = new LinkedHashMap<>();
        for (JobCandidates.JobCandidateItem item : items) {
            unique.putIfAbsent(item.jobId(), item);
        }
        return new ArrayList<>(unique.values());
    }

    // hasMore：只有 total/page/pageSize 齐全且可信时才计算，否则置空
    private Boolean resolveHasMore(JsonNode total, JsonNode page, JsonNode pageSize) {
        if (!total.isNumber() || !page.isNumber() || !pageSize.isNumber()) {
            return null;
        }
        long consumed = page.asLong() * pageSize.asLong();
        return consumed < total.asLong();
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            log.debug("岗位候选解析失败，按空结果处理，error={}", exception.getMessage());
            return null;
        }
    }
}
