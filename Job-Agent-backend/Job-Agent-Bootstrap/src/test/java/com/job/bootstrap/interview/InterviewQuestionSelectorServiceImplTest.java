package com.job.bootstrap.interview;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.job.bootstrap.mapper.InterviewQuestionBankMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.impl.InterviewQuestionSelectorServiceImpl;
import com.job.common.entity.interview.InterviewQuestionBank;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.rag.RagSearchResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewQuestionSelectorServiceImplTest {

    @Test
    void shouldPreferRagHitsDeduplicateAndBalanceDifficulty() {
        InterviewQuestionBankMapper questionBankMapper = mock(InterviewQuestionBankMapper.class);
        MockInterviewQuestionMapper mockQuestionMapper = mock(MockInterviewQuestionMapper.class);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        InterviewQuestionSelectorServiceImpl service = new InterviewQuestionSelectorServiceImpl(questionBankMapper, mockQuestionMapper, ragRetrievalService);

        InterviewQuestionBank easy = question(1L, "EASY", "Java 基础是什么？", "Java", "Java", 101L);
        InterviewQuestionBank medium = question(2L, "MEDIUM", "Spring Bean 生命周期是什么？", "Spring", "Spring,Java", 102L);
        InterviewQuestionBank hard = question(3L, "HARD", "Redis 缓存一致性怎么处理？", "Redis", "Redis,缓存", 103L);
        InterviewQuestionBank duplicateMedium = question(4L, "MEDIUM", "Spring Bean 生命周期是什么？", "Spring", "Spring", 104L);
        InterviewQuestionBank fallback = question(5L, "MEDIUM", "MySQL 索引优化怎么做？", "MySQL", "MySQL", 105L);

        when(ragRetrievalService.search(eq(0L), any(String.class), any(Integer.class))).thenReturn(List.of(
                ragResult(2L),
                ragResult(2L),
                ragResult(3L),
                ragResult(4L)
        ));
        when(questionBankMapper.selectBatchIds(any())).thenReturn(List.of(medium, hard, duplicateMedium));
        when(questionBankMapper.selectList(any(Wrapper.class))).thenReturn(List.of(easy, medium, hard, duplicateMedium, fallback));

        JobPosition job = new JobPosition();
        job.setJobTitle("Java 后端开发");
        job.setJobCategory("后端");
        job.setSkillKeywords("Java,Spring,Redis,MySQL");
        job.setJobDescription("负责 Spring Boot 和 Redis 缓存相关系统");
        job.setJobRequirement("熟悉 Java、MySQL、Redis");

        JobResume resume = new JobResume();
        resume.setRawText("做过 Java Spring Redis 项目");

        List<InterviewQuestionBank> selected = service.selectQuestions(job, resume, 3);

        assertThat(selected)
                .extracting(InterviewQuestionBank::getId)
                .containsExactly(1L, 2L, 3L);
        assertThat(selected)
                .extracting(InterviewQuestionBank::getDifficulty)
                .containsExactly("EASY", "MEDIUM", "HARD");
    }

    @Test
    void shouldExcludeRecentUserQuestionsAndBackfillWhenCandidatesAreInsufficient() {
        InterviewQuestionBankMapper questionBankMapper = mock(InterviewQuestionBankMapper.class);
        MockInterviewQuestionMapper mockQuestionMapper = mock(MockInterviewQuestionMapper.class);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        InterviewQuestionSelectorServiceImpl service = new InterviewQuestionSelectorServiceImpl(questionBankMapper, mockQuestionMapper, ragRetrievalService);

        InterviewQuestionBank recentQuestion = question(10L, "EASY", "Java 集合怎么选型？", "Java", "Java", 201L);
        InterviewQuestionBank freshQuestion = question(11L, "MEDIUM", "Spring 事务失效场景有哪些？", "Spring", "Spring,事务", 202L);

        when(ragRetrievalService.search(eq(0L), any(String.class), any(Integer.class))).thenReturn(List.of(
                ragResult(10L),
                ragResult(11L)
        ));
        when(questionBankMapper.selectBatchIds(any())).thenReturn(List.of(recentQuestion, freshQuestion));
        when(questionBankMapper.selectList(any(Wrapper.class))).thenReturn(List.of(recentQuestion, freshQuestion));
        when(mockQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(usedQuestion(10L)));

        JobPosition job = new JobPosition();
        job.setJobTitle("Java 后端开发");
        job.setSkillKeywords("Java,Spring");

        JobResume resume = new JobResume();
        resume.setRawText("熟悉 Java 和 Spring");

        List<InterviewQuestionBank> selected = service.selectQuestions(7L, job, resume, 2, 72);

        assertThat(selected)
                .extracting(InterviewQuestionBank::getId)
                .containsExactly(11L, 10L);
    }

    private InterviewQuestionBank question(
            Long id,
            String difficulty,
            String title,
            String category,
            String tags,
            Long ragChunkId
    ) {
        InterviewQuestionBank question = new InterviewQuestionBank();
        question.setId(id);
        question.setQuestionTitle(title);
        question.setStandardAnswer(title + " 的标准答案");
        question.setQuestionType("TECHNICAL");
        question.setCategory(category);
        question.setDifficulty(difficulty);
        question.setTags(tags);
        question.setRagChunkId(ragChunkId);
        question.setStatus("ACTIVE");
        question.setIsDeleted(0);
        return question;
    }

    private RagSearchResultVO ragResult(Long questionBankId) {
        RagSearchResultVO result = new RagSearchResultVO();
        result.setDocumentType("INTERVIEW_QUESTION");
        result.setMetadata(Map.of("questionBankId", questionBankId));
        result.setScore(0.9);
        return result;
    }

    private MockInterviewQuestion usedQuestion(Long questionBankId) {
        MockInterviewQuestion question = new MockInterviewQuestion();
        question.setQuestionBankId(questionBankId);
        question.setUserId(7L);
        question.setIsDeleted(0);
        return question;
    }
}
