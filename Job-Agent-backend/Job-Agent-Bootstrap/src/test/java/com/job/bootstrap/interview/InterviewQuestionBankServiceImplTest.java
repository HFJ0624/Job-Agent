package com.job.bootstrap.interview;

import com.job.bootstrap.interview.model.InterviewQuestionImportItem;
import com.job.bootstrap.interview.parser.InterviewQuestionMarkdownParser;
import com.job.bootstrap.mapper.InterviewQuestionBankMapper;
import com.job.bootstrap.rag.model.RagDocumentSource;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.rag.service.RagKnowledgeService;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.bootstrap.rag.utils.RagTextSplitter;
import com.job.bootstrap.service.impl.InterviewQuestionBankServiceImpl;
import com.job.common.entity.interview.InterviewQuestionBank;
import com.job.common.entity.rag.RagChunk;
import com.job.common.vo.rag.RagIndexResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewQuestionBankServiceImplTest {

    @Test
    void shouldIndexQuestionAsInterviewQuestionRagDocument() {
        InterviewQuestionBankMapper questionMapper = mock(InterviewQuestionBankMapper.class);
        RagTextSplitter ragTextSplitter = mock(RagTextSplitter.class);
        RagEmbeddingService ragEmbeddingService = mock(RagEmbeddingService.class);
        RagVectorStoreService ragVectorStoreService = mock(RagVectorStoreService.class);
        RagKnowledgeService ragKnowledgeService = mock(RagKnowledgeService.class);

        InterviewQuestionBank question = new InterviewQuestionBank();
        question.setId(12L);
        question.setQuestionTitle("Spring Bean 生命周期是什么？");
        question.setStandardAnswer("Spring Bean 生命周期包括实例化、属性填充、Aware 回调、初始化、销毁等阶段。");
        question.setQuestionType("TECHNICAL");
        question.setCategory("Java");
        question.setDifficulty("MEDIUM");
        question.setTags("Spring,Bean");
        question.setSourceFile("demo.md");
        question.setStatus("ACTIVE");

        RagChunk chunk = new RagChunk();
        chunk.setId(101L);
        chunk.setDocumentId(88L);
        chunk.setUserId(0L);
        chunk.setBusinessId(12L);
        chunk.setChunkIndex(0);
        chunk.setTitle("面试题:Spring Bean 生命周期是什么？");
        chunk.setContent("chunk text");
        chunk.setContentHash("abc");
        chunk.setSource("interview_question_bank");

        when(questionMapper.selectById(12L)).thenReturn(question);
        when(ragTextSplitter.split(any())).thenReturn(List.of("chunk text"));
        when(ragKnowledgeService.saveDocumentChunks(any(RagDocumentSource.class), anyList())).thenReturn(List.of(chunk));
        when(ragEmbeddingService.embed("chunk text")).thenReturn(new float[]{0.1F, 0.2F});

        InterviewQuestionBankServiceImpl service = new InterviewQuestionBankServiceImpl(
                questionMapper,
                new InterviewQuestionMarkdownParser(),
                ragTextSplitter,
                ragEmbeddingService,
                ragVectorStoreService,
                ragKnowledgeService
        );

        RagIndexResultVO result = service.indexQuestion(12L);

        assertThat(result.getIndexedDocumentCount()).isEqualTo(1);
        assertThat(result.getIndexedChunkCount()).isEqualTo(1);
        assertThat(question.getRagDocumentId()).isEqualTo(88L);
        assertThat(question.getRagChunkId()).isEqualTo(101L);
        verify(ragVectorStoreService).saveChunks(anyList());
        verify(ragKnowledgeService).markDocumentIndexed(0L, "INTERVIEW_QUESTION", 12L);
        verify(questionMapper).updateById(question);
    }
}
