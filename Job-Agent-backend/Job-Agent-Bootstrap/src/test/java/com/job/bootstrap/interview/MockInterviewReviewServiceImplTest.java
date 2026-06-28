package com.job.bootstrap.interview;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.MockInterviewAnswerMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.mapper.MockInterviewReviewRecordMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.impl.MockInterviewReviewServiceImpl;
import com.job.common.entity.interview.MockInterviewAnswer;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.interview.MockInterviewReviewRecord;
import com.job.common.entity.interview.MockInterviewSession;
import com.job.common.vo.interview.MockInterviewStudyPlanVO;
import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockInterviewReviewServiceImplTest {

    @Test
    void shouldGenerateReviewByLlmAndSaveRecord() {
        MockInterviewReviewRecordMapper reviewMapper = mock(MockInterviewReviewRecordMapper.class);
        MockInterviewSessionMapper sessionMapper = mock(MockInterviewSessionMapper.class);
        MockInterviewQuestionMapper questionMapper = mock(MockInterviewQuestionMapper.class);
        MockInterviewAnswerMapper answerMapper = mock(MockInterviewAnswerMapper.class);
        AiModelGatewayService aiModelGatewayService = mock(AiModelGatewayService.class);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        MockInterviewReviewServiceImpl service = new MockInterviewReviewServiceImpl(
                reviewMapper,
                sessionMapper,
                questionMapper,
                answerMapper,
                aiModelGatewayService,
                ragRetrievalService,
                new ObjectMapper()
        );

        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(answerMapper.selectList(any(Wrapper.class))).thenReturn(List.of(answer()));
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(question()));
        when(aiModelGatewayService.chat(
                eq("MOCK_INTERVIEW_REVIEW_GENERATE"),
                any(),
                any(String.class),
                eq(7L),
                any(String.class)
        )).thenReturn("""
                {
                  "totalScore": 82,
                  "reviewLevel": "良好",
                  "strengthSummary": "回答能覆盖主要知识点，表达基本清晰。",
                  "weaknessSummary": "高并发方案展开不够深入。",
                  "abilityTags": ["Java 基础较稳", "并发深度不足"],
                  "weakQuestions": ["Redis 缓存一致性怎么处理？"],
                  "improvementPlan": "继续补充 Redis 一致性、限流和降级方案。"
                }
                """);

        MockInterviewReviewVO result = service.generateReview(7L, 100L);

        assertThat(result.getTotalScore()).isEqualByComparingTo("82.00");
        assertThat(result.getReviewLevel()).isEqualTo("良好");
        assertThat(result.getSource()).isEqualTo("LLM");
        assertThat(result.getAbilityTags()).containsExactly("Java 基础较稳", "并发深度不足");

        ArgumentCaptor<MockInterviewReviewRecord> captor = ArgumentCaptor.forClass(MockInterviewReviewRecord.class);
        verify(reviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("LLM");
        assertThat(captor.getValue().getStrengthSummary()).contains("主要知识点");
    }

    @Test
    void shouldFailWhenLlmReviewJsonCannotBeParsed() {
        MockInterviewReviewRecordMapper reviewMapper = mock(MockInterviewReviewRecordMapper.class);
        MockInterviewSessionMapper sessionMapper = mock(MockInterviewSessionMapper.class);
        MockInterviewQuestionMapper questionMapper = mock(MockInterviewQuestionMapper.class);
        MockInterviewAnswerMapper answerMapper = mock(MockInterviewAnswerMapper.class);
        AiModelGatewayService aiModelGatewayService = mock(AiModelGatewayService.class);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        MockInterviewReviewServiceImpl service = new MockInterviewReviewServiceImpl(
                reviewMapper,
                sessionMapper,
                questionMapper,
                answerMapper,
                aiModelGatewayService,
                ragRetrievalService,
                new ObjectMapper()
        );

        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(answerMapper.selectList(any(Wrapper.class))).thenReturn(List.of(answer()));
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(question()));
        when(aiModelGatewayService.chat(
                eq("MOCK_INTERVIEW_REVIEW_GENERATE"),
                any(),
                any(String.class),
                eq(7L),
                any(String.class)
        )).thenReturn("这次整体还不错，但我没有返回 JSON");

        assertThatThrownBy(() -> service.generateReview(7L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("AI复盘结果解析失败");
        verify(reviewMapper, never()).insert(any(MockInterviewReviewRecord.class));
    }

    @Test
    void shouldBuildStudyPlanFromLatestReviewAndRagResults() {
        MockInterviewReviewRecordMapper reviewMapper = mock(MockInterviewReviewRecordMapper.class);
        MockInterviewSessionMapper sessionMapper = mock(MockInterviewSessionMapper.class);
        MockInterviewQuestionMapper questionMapper = mock(MockInterviewQuestionMapper.class);
        MockInterviewAnswerMapper answerMapper = mock(MockInterviewAnswerMapper.class);
        AiModelGatewayService aiModelGatewayService = mock(AiModelGatewayService.class);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        MockInterviewReviewServiceImpl service = new MockInterviewReviewServiceImpl(
                reviewMapper,
                sessionMapper,
                questionMapper,
                answerMapper,
                aiModelGatewayService,
                ragRetrievalService,
                new ObjectMapper()
        );

        MockInterviewReviewRecord review = new MockInterviewReviewRecord();
        review.setId(88L);
        review.setUserId(7L);
        review.setSessionId(100L);
        review.setWeakQuestions("[\"Redis 缓存一致性怎么处理？\"]");
        review.setAbilityTags("[\"并发深度不足\"]");
        review.setImprovementPlan("继续补充 Redis 一致性、限流和降级方案。");
        review.setIsDeleted(0);

        when(sessionMapper.selectById(100L)).thenReturn(session());
        when(reviewMapper.selectOne(any(Wrapper.class))).thenReturn(review);
        when(ragRetrievalService.search(eq(7L), any(String.class), eq(3))).thenReturn(List.of(studyRagResult()));

        MockInterviewStudyPlanVO plan = service.buildStudyPlan(7L, 100L);

        assertThat(plan.getItems()).hasSize(2);
        assertThat(plan.getItems().get(0).getKnowledgePoint()).contains("Redis");
        assertThat(plan.getItems().get(0).getMaterials()).hasSize(1);
        assertThat(plan.getItems().get(0).getMaterials().get(0).getTitle()).isEqualTo("Redis 缓存一致性");
    }

    private MockInterviewSession session() {
        MockInterviewSession session = new MockInterviewSession();
        session.setId(100L);
        session.setUserId(7L);
        session.setJobId(200L);
        session.setJobTitle("Java 后端开发");
        session.setCompanyName("测试公司");
        session.setApplicationId(300L);
        return session;
    }

    private MockInterviewQuestion question() {
        MockInterviewQuestion question = new MockInterviewQuestion();
        question.setId(10L);
        question.setUserId(7L);
        question.setSessionId(100L);
        question.setQuestionType("TECHNICAL");
        question.setQuestionContent("Redis 缓存一致性怎么处理？");
        question.setStandardAnswer("可以使用延迟双删、消息队列补偿、设置合理过期时间。");
        return question;
    }

    private MockInterviewAnswer answer() {
        MockInterviewAnswer answer = new MockInterviewAnswer();
        answer.setId(20L);
        answer.setUserId(7L);
        answer.setSessionId(100L);
        answer.setQuestionId(10L);
        answer.setAnswerContent("我会先更新数据库，再删除缓存，必要时做重试补偿。");
        answer.setScore(BigDecimal.valueOf(78));
        answer.setLevel("良好");
        answer.setStrengths("[\"覆盖了先更新数据库再删缓存\"]");
        answer.setProblems("[\"缺少延迟双删和消息补偿\"]");
        answer.setSuggestions("[\"补充失败重试和最终一致性方案\"]");
        return answer;
    }

    private RagSearchResultVO studyRagResult() {
        RagSearchResultVO result = new RagSearchResultVO();
        result.setChunkId(501L);
        result.setDocumentId(401L);
        result.setTitle("Redis 缓存一致性");
        result.setReferenceTitle("Redis 面试知识库");
        result.setContent("缓存一致性常见方案包括延迟双删、消息队列补偿、binlog 订阅和合理过期时间。");
        result.setScore(0.91);
        result.setRetrievalSource("HYBRID");
        return result;
    }
}
