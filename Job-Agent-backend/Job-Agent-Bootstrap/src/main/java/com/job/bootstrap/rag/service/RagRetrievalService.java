package com.job.bootstrap.rag.service;

import com.job.common.dto.rag.RagRetrievalEvalDTO;
import com.job.common.vo.rag.RagRetrievalEvalVO;
import com.job.common.vo.rag.RagSearchResultVO;

import java.util.List;

/**
 * 作者:hfj
 * 功能:RAG 知识库检索服务接口
 * 日期:2026/6/14
 */
public interface RagRetrievalService {

    /**
     * 根据自然语言问题检索相关知识。
     *
     * @param userId 当前登录用户 ID
     * @param query 用户问题或检索词
     * @param limit 召回数量，为空时使用默认配置
     * @return 相似度最高的知识分片
     */
    List<RagSearchResultVO> search(Long userId, String query, Integer limit);

    /**
     * 后台评测单条 query 的召回质量。
     *
     * @param query 评测参数
     * @return 命中、排名和本次召回明细
     */
    RagRetrievalEvalVO evaluate(RagRetrievalEvalDTO query);
}
