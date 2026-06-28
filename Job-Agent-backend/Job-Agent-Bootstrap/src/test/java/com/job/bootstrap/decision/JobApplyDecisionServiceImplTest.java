package com.job.bootstrap.decision;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobApplyDecisionRecordMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.JobMatchService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.impl.JobApplyDecisionServiceImpl;
import com.job.common.entity.decision.JobApplyDecisionRecord;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.decision.JobApplyDecisionVO;
import com.job.common.vo.match.JobMatchVO;
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

class JobApplyDecisionServiceImplTest {

    @Test
    void shouldGenerateLlmDecisionAndSaveRecord() {
        JobApplyDecisionRecordMapper decisionMapper = mock(JobApplyDecisionRecordMapper.class);
        JobResumeService resumeService = mock(JobResumeService.class);
        JobPositionService positionService = mock(JobPositionService.class);
        JobMatchService jobMatchService = mock(JobMatchService.class);
        AiModelGatewayService aiModelGatewayService = mock(AiModelGatewayService.class);
        JobApplyDecisionServiceImpl service = new JobApplyDecisionServiceImpl(
                decisionMapper,
                resumeService,
                positionService,
                jobMatchService,
                aiModelGatewayService,
                new ObjectMapper()
        );

        when(resumeService.getUserResumeRequired(7L, 11L)).thenReturn(resume());
        when(positionService.getPositionRequired(22L)).thenReturn(job());
        when(jobMatchService.getLatestMatch(7L, 11L, 22L)).thenReturn(match());
        when(aiModelGatewayService.chat(eq("JOB_APPLY_DECISION_GENERATE"), any(), any(String.class), eq(7L), any(String.class)))
                .thenReturn("""
                        {
                          "decision": "APPLY",
                          "decisionLabel": "建议投递",
                          "decisionScore": 86,
                          "reason": "岗位技能和项目经历匹配度较高，可以优先投递。",
                          "risks": ["Redis 高并发经验需要补充"],
                          "resumeSuggestions": ["突出 Spring Boot 项目中的性能优化结果"],
                          "interviewSuggestions": ["准备 Redis 缓存一致性案例"],
                          "nextActions": ["优化简历后投递", "投递前生成打招呼语"]
                        }
                        """);

        JobApplyDecisionVO result = service.generateDecision(7L, 11L, 22L);

        assertThat(result.getDecision()).isEqualTo("APPLY");
        assertThat(result.getDecisionLabel()).isEqualTo("建议投递");
        assertThat(result.getDecisionScore()).isEqualByComparingTo("86.00");
        assertThat(result.getRisks()).containsExactly("Redis 高并发经验需要补充");

        ArgumentCaptor<JobApplyDecisionRecord> captor = ArgumentCaptor.forClass(JobApplyDecisionRecord.class);
        verify(decisionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("LLM");
        assertThat(captor.getValue().getJobTitle()).isEqualTo("Java 后端开发");
    }

    @Test
    void shouldFailWhenModelReturnsInvalidJson() {
        JobApplyDecisionRecordMapper decisionMapper = mock(JobApplyDecisionRecordMapper.class);
        JobResumeService resumeService = mock(JobResumeService.class);
        JobPositionService positionService = mock(JobPositionService.class);
        JobMatchService jobMatchService = mock(JobMatchService.class);
        AiModelGatewayService aiModelGatewayService = mock(AiModelGatewayService.class);
        JobApplyDecisionServiceImpl service = new JobApplyDecisionServiceImpl(
                decisionMapper,
                resumeService,
                positionService,
                jobMatchService,
                aiModelGatewayService,
                new ObjectMapper()
        );

        when(resumeService.getUserResumeRequired(7L, 11L)).thenReturn(resume());
        when(positionService.getPositionRequired(22L)).thenReturn(job());
        when(jobMatchService.getLatestMatch(7L, 11L, 22L)).thenReturn(match());
        when(aiModelGatewayService.chat(eq("JOB_APPLY_DECISION_GENERATE"), any(), any(String.class), eq(7L), any(String.class)))
                .thenReturn("建议投递，但不是 JSON");

        assertThatThrownBy(() -> service.generateDecision(7L, 11L, 22L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("AI投递决策解析失败");
        verify(decisionMapper, never()).insert(any(JobApplyDecisionRecord.class));
    }

    @Test
    void shouldReturnLatestDecision() {
        JobApplyDecisionRecordMapper decisionMapper = mock(JobApplyDecisionRecordMapper.class);
        JobApplyDecisionServiceImpl service = new JobApplyDecisionServiceImpl(
                decisionMapper,
                mock(JobResumeService.class),
                mock(JobPositionService.class),
                mock(JobMatchService.class),
                mock(AiModelGatewayService.class),
                new ObjectMapper()
        );
        JobApplyDecisionRecord record = new JobApplyDecisionRecord();
        record.setId(1L);
        record.setResumeId(11L);
        record.setJobId(22L);
        record.setDecision("CAUTIOUS");
        record.setDecisionLabel("谨慎投递");
        record.setDecisionScore(BigDecimal.valueOf(72));
        when(decisionMapper.selectOne(any(Wrapper.class))).thenReturn(record);

        JobApplyDecisionVO latest = service.getLatestDecision(7L, 11L, 22L);

        assertThat(latest.getDecision()).isEqualTo("CAUTIOUS");
    }

    private JobResume resume() {
        JobResume resume = new JobResume();
        resume.setId(11L);
        resume.setUserId(7L);
        resume.setResumeName("Java 后端简历");
        resume.setRawText("熟悉 Java、Spring Boot、MySQL、Redis，有电商项目经验。");
        return resume;
    }

    private JobPosition job() {
        JobPosition job = new JobPosition();
        job.setId(22L);
        job.setJobTitle("Java 后端开发");
        job.setCompanyId(33L);
        job.setCity("上海");
        job.setSkillKeywords("Java,Spring Boot,MySQL,Redis");
        job.setJobDescription("负责后端服务开发和性能优化。");
        job.setJobRequirement("熟悉 Java、Spring Boot、Redis，有高并发经验。");
        job.setStatus(1);
        return job;
    }

    private JobMatchVO match() {
        JobMatchVO match = new JobMatchVO();
        match.setId(44L);
        match.setMatchScore(BigDecimal.valueOf(82));
        match.setMatchLevel("较匹配");
        match.setRecommendApply(true);
        match.setMatchedSkills(List.of("Java", "Spring Boot", "MySQL"));
        match.setMissingSkills(List.of("Redis 高并发"));
        match.setAdvantages(List.of("项目经验与岗位较匹配"));
        match.setRiskPoints(List.of("Redis 高并发经验不足"));
        match.setSuggestions(List.of("补充 Redis 缓存一致性项目案例"));
        return match;
    }
}
