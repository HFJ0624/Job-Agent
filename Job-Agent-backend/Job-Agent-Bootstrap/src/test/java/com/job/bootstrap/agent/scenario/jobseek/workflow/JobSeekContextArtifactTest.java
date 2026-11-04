package com.job.bootstrap.agent.scenario.jobseek.workflow;

import com.job.bootstrap.agent.scenario.jobseek.service.SeekArtifactService;
import com.job.bootstrap.agent.scenario.jobseek.service.SeekContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.domain.job.JobSearchWorkflow;
import com.job.bootstrap.agent.domain.match.MatchAgentNode;
import com.job.bootstrap.agent.domain.resume.ResumeAgentNode;
import com.job.bootstrap.agent.domain.resume.ResumeProfile;
import com.job.bootstrap.agent.domain.resume.ResumeSelectionService;
import com.job.bootstrap.agent.orchestration.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.runtime.react.AgentResult;
import com.job.bootstrap.agent.runtime.artifact.AgentArtifactService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.entity.agent.AgentArtifact;
import com.job.common.entity.resume.JobResume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定点验证（问题二/问题三）:
 * 1. 简历待选择时，NEED_INPUT 也要带回本轮已合并的搜索条件（城市/关键词）；
 * 2. 画像成功、搜索失败时，画像产物必须已保存，重试可复用（不再跑画像节点）。
 */
class JobSeekContextArtifactTest {

    private static final Long USER_ID = 100L;
    private static final Long PLAN_ID = 900L;

    private final ResumeAgentNode resumeNode = mock(ResumeAgentNode.class);
    private final MatchAgentNode matchNode = mock(MatchAgentNode.class);
    private final JobSearchWorkflow searchWorkflow = mock(JobSearchWorkflow.class);
    private final JobResumeService resumeService = mock(JobResumeService.class);
    private final UserJobPreferenceService preferenceService = mock(UserJobPreferenceService.class);
    private final AgentArtifactService artifactService = mock(AgentArtifactService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private JobSeekWorkflow workflow;

    @BeforeEach
    void setUp() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.initialize();
        workflow = new JobSeekWorkflow(resumeNode, matchNode, searchWorkflow, resumeService,
                new ResumeSelectionService(resumeService), preferenceService, artifactService,
                new SeekArtifactService(artifactService, mapper),
                new SeekContextResolver(new com.job.bootstrap.agent.orchestration.plan.AgentPlanParameterExtractor(),
                        new com.job.bootstrap.agent.domain.resume.ResumeSelectionService(resumeService),
                        resumeService, preferenceService, mapper),
                new AgentPlanParameterExtractor(), mapper, executor);
    }

    private JobResume resume(Long id, String name) {
        JobResume resume = new JobResume();
        resume.setId(id);
        resume.setUserId(USER_ID);
        resume.setResumeName(name);
        resume.setRawText("Java 后端，3 年经验，熟悉 Spring Boot");
        return resume;
    }

    @Test
    void resumeSelectionShouldStillCarryMergedConstraints() {
        // 两份简历且没有默认 → 需要用户选择
        when(resumeService.listUserResumes(USER_ID)).thenReturn(List.of(
                resume(11L, "后端简历"), resume(12L, "算法简历")));

        JobSeekWorkflow.JobSeekOutcome outcome = workflow.execute(new JobSeekWorkflow.JobSeekInput(
                USER_ID, 10L, PLAN_ID, "trace", "找上海 Java 岗位", "{}"));

        assertThat(outcome.status()).isEqualTo("NEED_INPUT");
        assertThat(outcome.constraints()).isNotNull();
        assertThat(outcome.constraints().city()).isEqualTo("上海");
        verify(artifactService, never()).save(any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void profileArtifactShouldBeSavedWhenSearchFailsAndReusedOnRetry() {
        when(resumeService.listUserResumes(USER_ID)).thenReturn(List.of(resume(11L, "后端简历")));
        when(preferenceService.getPreference(USER_ID)).thenReturn(null);

        ResumeProfile profile = new ResumeProfile(11L, "v1", "后端简历", "Java", false,
                List.of("Java"), List.of("订单系统"), List.of(), List.of(), List.of(), "COMPLETED", null);
        when(resumeNode.execute(anyLong(), anyLong(), anyLong(), anyString(), any(), anyString(), any(), any()))
                .thenReturn(new ResumeAgentNode.Result(profile, null, "SUCCEEDED", 1L, List.of(), 1, 1));

        // 画像产物首次不存在 → 保存；搜索失败（无候选）
        when(artifactService.findLatest(any(), any(), eq(AgentArtifactService.TYPE_RESUME_PROFILE), any()))
                .thenReturn(null);
        AgentArtifact saved = new AgentArtifact();
        saved.setId(501L);
        when(artifactService.save(any(), any(), any(), eq(AgentArtifactService.TYPE_RESUME_PROFILE),
                any(), any(), any())).thenReturn(saved);
        when(artifactService.findLatest(any(), any(), eq(AgentArtifactService.TYPE_JOB_CANDIDATES), any()))
                .thenReturn(null);
        when(searchWorkflow.execute(any())).thenReturn(new JobSearchWorkflow.WorkflowOutcome(
                AgentResult.Outcome.FAILED, "SEARCH_NODE_FAILED", "搜索失败",
                List.of(), List.of(), null, List.of(), 1, 1));

        JobSeekWorkflow.JobSeekOutcome outcome = workflow.execute(new JobSeekWorkflow.JobSeekInput(
                USER_ID, 10L, PLAN_ID, "trace", "找上海 Java 岗位", "{}"));

        assertThat(outcome.status()).isEqualTo("FAILED");
        // 画像已成功 → 产物必须已保存并带回 ID
        assertThat(outcome.profileArtifactId()).isEqualTo(501L);

        // 第二次（重试）：同版本画像产物存在 → 不再调用画像节点
        AgentArtifact cached = new AgentArtifact();
        cached.setId(501L);
        try {
            cached.setContentJson(mapper.writeValueAsString(profile));
        } catch (Exception ignored) {
            // 测试数据序列化失败即视为用例失败
        }
        when(artifactService.findLatest(any(), any(), eq(AgentArtifactService.TYPE_RESUME_PROFILE), any()))
                .thenReturn(cached);

        workflow.execute(new JobSeekWorkflow.JobSeekInput(
                USER_ID, 10L, PLAN_ID, "trace", "找上海 Java 岗位", "{}"));

        // 两次执行里画像节点只应被调用一次（第二次复用产物，不再跑 Agent）
        verify(resumeNode, times(1)).execute(anyLong(), anyLong(), anyLong(), anyString(),
                any(), anyString(), any(), any());
    }
}
