package com.job.ai.agent.port;

import com.job.common.vo.rag.RagSearchResultVO;

import java.util.List;

/**
 * 功能: Agent 知识库检索端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>只暴露检索能力；切片、权限过滤、重排与向量库访问保持原有实现。</p>
 */
public interface RagSearchPort {

    List<RagSearchResultVO> search(Long userId, String query, Integer limit);
}
