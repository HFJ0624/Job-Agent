package com.job.bootstrap.integration.agent.rag;

import com.job.agent.port.EmbeddingPort;
import com.job.bootstrap.application.rag.service.RagEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 功能: 文本向量化端口的宿主实现，委托现有 RagEmbeddingService。 */
@Component
@RequiredArgsConstructor
public class EmbeddingAdapter implements EmbeddingPort {

    private final RagEmbeddingService ragEmbeddingService;

    @Override
    public float[] embed(String text) {
        return ragEmbeddingService.embed(text);
    }
}
