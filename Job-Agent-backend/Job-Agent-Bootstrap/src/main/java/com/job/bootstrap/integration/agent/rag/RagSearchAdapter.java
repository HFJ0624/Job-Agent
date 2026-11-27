package com.job.bootstrap.integration.agent.rag;

import com.job.agent.port.RagSearchPort;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.common.vo.rag.RagSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 功能: Agent 知识库检索端口的宿主实现，委托现有 RagRetrievalService。 */
@Component
@RequiredArgsConstructor
public class RagSearchAdapter implements RagSearchPort {

    private final RagRetrievalService ragRetrievalService;

    @Override
    public List<RagSearchResultVO> search(Long userId, String query, Integer limit) {
        return ragRetrievalService.search(userId, query, limit);
    }
}
