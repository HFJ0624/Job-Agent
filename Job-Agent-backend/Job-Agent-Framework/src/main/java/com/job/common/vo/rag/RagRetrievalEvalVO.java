package com.job.common.vo.rag;

import lombok.Data;

import java.util.List;

/**
 * 作者:hfj
 * 功能:RAG 召回质量评测结果
 * 日期:2026/6/20
 */
@Data
public class RagRetrievalEvalVO {

    /**
     * 评测用户 ID。
     */
    private Long userId;

    /**
     * 评测问题。
     */
    private String query;

    /**
     * 本次要求返回的最大条数。
     */
    private Integer limit;

    /**
     * 实际召回条数。
     */
    private Integer retrievedCount;

    /**
     * 是否命中期望结果。
     */
    private Boolean hit;

    /**
     * 命中的排名，从 1 开始。
     * 未命中时为空。
     */
    private Integer hitRank;

    /**
     * 倒数排名分。
     * 命中第 1 条为 1，命中第 2 条为 0.5，未命中为 0。
     */
    private Double reciprocalRank;

    /**
     * 后台传入的期望目标描述。
     */
    private String expectedTarget;

    /**
     * 评测说明，便于后台直接展示为什么命中或未命中。
     */
    private String message;

    /**
     * 本次实际召回结果。
     */
    private List<RagSearchResultVO> results;
}
