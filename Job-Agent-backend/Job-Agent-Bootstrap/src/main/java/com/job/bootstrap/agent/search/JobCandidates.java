package com.job.bootstrap.agent.search;

import java.util.List;
import java.util.Map;

/**
 * 功能: 统一岗位候选结构（第一阶段）。
 *
 * 解决什么问题: JobSearchTool 返回对象（total + jobs 数组），JobRecommendTool 返回裸数组——
 * 两个工具结构不一致，ReAct/Workflow 无法统一处理。适配层统一成 JobCandidates：
 * 分页/总数字段**能确定才填**，确定不了就置空（文档要求：不能编造分页和总数）。
 *
 * @param items       候选列表（已去重，保持首次出现顺序）
 * @param total       总命中数（推荐工具等无法可靠取得时为 null）
 * @param page        页码（未知为 null）
 * @param pageSize    页大小（未知为 null）
 * @param hasMore     是否还有更多（无法判断为 null）
 * @param source      来源：job_search / job_recommend
 * @param retrievedAt 获取时间戳
 */
public record JobCandidates(
        List<JobCandidateItem> items,
        Long total,
        Integer page,
        Integer pageSize,
        Boolean hasMore,
        String source,
        long retrievedAt
) {

    /**
     * 单条候选：jobId + 原始字段（保留原始 Map，便于服务端重建对外内容，不依赖模型转述）。
     */
    public record JobCandidateItem(Long jobId, Map<String, Object> raw) {
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    public List<Long> jobIds() {
        return items == null ? List.of() : items.stream().map(JobCandidateItem::jobId).toList();
    }

    public static JobCandidates empty(String source) {
        return new JobCandidates(List.of(), null, null, null, null, source, System.currentTimeMillis());
    }
}
