package com.job.bootstrap.agent.orchestration.seek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.runtime.artifact.AgentArtifactService;
import com.job.bootstrap.agent.runtime.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.runtime.executor.AgentToolInvoker;
import com.job.bootstrap.agent.domain.match.MatchAgentNode;
import com.job.bootstrap.agent.domain.match.MatchReportContract;
import com.job.bootstrap.agent.orchestration.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.runtime.react.ReActRunner;
import com.job.bootstrap.agent.runtime.react.TerminalToolContract;
import com.job.bootstrap.agent.domain.resume.ResumeAgentNode;
import com.job.bootstrap.agent.domain.resume.ResumeProfile;
import com.job.bootstrap.agent.domain.resume.ResumeProfileContract;
import com.job.bootstrap.agent.runtime.run.AgentRunService;
import com.job.bootstrap.agent.domain.job.JobCandidatesAdapter;
import com.job.bootstrap.agent.domain.job.JobSearchWorkflow;
import com.job.bootstrap.ai.turn.AiChatTurnResponse;
import com.job.bootstrap.ai.turn.AiChatTurnRequest;
import com.job.bootstrap.ai.turn.AiToolCall;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.JobMatchService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.entity.agent.AgentArtifact;
import com.job.common.entity.agent.AgentRun;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.match.JobMatchVO;
import com.job.common.vo.match.MatchEvidenceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第二阶段复合任务验证（最小集）：
 * 1. 正常复合链路：画像 → 搜索 → 匹配，最终答复用可信字段重建；
 * 2. 缺少简历：进入 NEED_INPUT（不编造匹配报告）；
 * 3. 条件变化（城市）：画像复用、搜索与匹配按新条件版本重跑。
 */
class JobSeekWorkflowTest {

    private static final Long USER_ID = 100L;
    private static final Long PLAN_ID = 900L;

    private static final String RESUME_TEXT = "张三，本科，3年Java后端开发经验。熟悉 Spring Boot、MySQL、Redis。"
            + "在电商系统负责订单模块，接口优化后响应时间降低 50%。";

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final AgentToolInvoker invoker = mock(AgentToolInvoker.class);
    private final AgentRunService runService = mock(AgentRunService.class);
    private final JobResumeService resumeService = mock(JobResumeService.class);
    private final JobPositionService positionService = mock(JobPositionService.class);
    private final JobMatchService matchService = mock(JobMatchService.class);
    private final UserJobPreferenceService preferenceService = mock(UserJobPreferenceService.class);
    private final AgentArtifactService artifactService = mock(AgentArtifactService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final ThreadPoolTaskExecutor executor = executor();

    private JobSeekWorkflow workflow;

    private static ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(8);
        executor.initialize();
        return executor;
    }

    @BeforeEach
    void setUp() {
        ReActRunner runner = new ReActRunner(gateway, new TerminalToolContract(mapper), mapper);
        ResumeAgentNode resumeNode = new ResumeAgentNode(runner, new ResumeProfileContract(mapper), runService, mapper);
        MatchAgentNode matchNode = new MatchAgentNode(runner, new MatchReportContract(mapper), runService,
                positionService, matchService, mapper);
        JobSearchWorkflow searchWorkflow = new JobSearchWorkflow(runner, new TerminalToolContract(mapper),
                invoker, new JobCandidatesAdapter(mapper));

        workflow = new JobSeekWorkflow(resumeNode, matchNode, searchWorkflow, resumeService,
                new com.job.bootstrap.agent.domain.resume.ResumeSelectionService(resumeService),
                preferenceService, artifactService,
                new SeekArtifactService(artifactService, mapper),
                new SeekContextResolver(new com.job.bootstrap.agent.orchestration.plan.AgentPlanParameterExtractor(),
                        new com.job.bootstrap.agent.domain.resume.ResumeSelectionService(resumeService),
                        resumeService, preferenceService, mapper), new AgentPlanParameterExtractor(), mapper, executor);

        // run 生命周期
        AgentRun run = new AgentRun();
        run.setId(1L);
        run.setAttempt(0);
        when(runService.createForNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(run);
        when(runService.claim(anyLong(), anyInt())).thenReturn(true);
        when(runService.finish(any(), anyInt(), any(), any(), any(), any())).thenReturn(true);
        when(runService.saveMessages(any(), anyInt(), any())).thenReturn(true);

        // 简历：唯一一份
        when(resumeService.listUserResumes(USER_ID)).thenReturn(List.of(resume()));
        when(resumeService.getUserResumeRequired(USER_ID, 11L)).thenReturn(resume());
        when(preferenceService.getPreference(USER_ID)).thenReturn(null);

        // 搜索工具真实返回
        when(invoker.invoke(eq(JobSearchWorkflow.SEARCH_TOOL), any(), any()))
                .thenReturn(AgentToolExecutionResult.builder().success(true)
                        .dataJson("""
                                {"total":1,"page":1,"pageSize":5,"jobs":[
                                  {"jobId":21,"jobTitle":"Java 后端开发","companyName":"A公司","city":"上海",
                                   "minSalary":20000,"maxSalary":30000,"skillKeywords":"Java,Spring Boot"}
                                ]}""").build());
        JobPosition job = new JobPosition();
        job.setId(21L);
        job.setJobTitle("Java 后端开发");
        job.setCity("上海");
        job.setMinSalary(20000);
        job.setMaxSalary(30000);
        job.setSkillKeywords("Java,Spring Boot");
        job.setJobDescription("负责后端接口开发");
        when(positionService.getById(21L)).thenReturn(job);

        JobMatchVO preview = new JobMatchVO();
        preview.setMatchScore(new BigDecimal("82"));
        preview.setMatchLevel("较匹配");
        preview.setMatchedSkills(List.of("Java", "Spring Boot"));
        preview.setMissingSkills(List.of("Redis"));
        preview.setEvidences(List.of(new MatchEvidenceVO("SKILL", "Spring Boot", "熟悉 Spring Boot", true)));
        when(matchService.previewMatch(anyLong(), anyLong(), eq(21L), any())).thenReturn(preview);

        // 产物：默认无历史，保存返回递增 ID
        when(artifactService.findLatest(any(), any(), anyString(), any())).thenReturn(null);
        AtomicLong artifactId = new AtomicLong(1000);
        when(artifactService.save(any(), any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            AgentArtifact artifact = new AgentArtifact();
            artifact.setId(artifactId.incrementAndGet());
            return artifact;
        });
    }

    private JobResume resume() {
        JobResume resume = new JobResume();
        resume.setId(11L);
        resume.setUserId(USER_ID);
        resume.setResumeName("张三简历");
        resume.setRawText(RESUME_TEXT);
        resume.setIsDefault(1);
        return resume;
    }

    private AiChatTurnResponse tool(String id, String name, String args) {
        return new AiChatTurnResponse(null, List.of(new AiToolCall(id, name, args)), "tool_calls", 1, 1);
    }

    private void stubCompositeTurns() {
        when(gateway.chatTurn(anyString(), any(), any(AiChatTurnRequest.class), anyLong(), anyString())).thenReturn(
                // 画像：读摘要 → 提交画像（证据 quote 必须是原文片段）
                tool("r1", "read_resume", "{}"),
                new AiChatTurnResponse(null, List.of(new AiToolCall("r2", ResumeProfileContract.TOOL_NAME,
                        """
                                {"resumeId":11,"outcome":"COMPLETED","skills":["Java","Spring Boot","MySQL"],
                                 "experiences":["电商订单模块"],
                                 "evidence":[{"fact":"熟悉 Spring Boot","sectionRef":"S1","quote":"熟悉 Spring Boot"}],
                                 "targetRole":"Java","targetRoleInferred":false}""")), "tool_calls", 1, 1),
                // 搜索：查岗位 → 终结
                tool("s1", "job_search", "{\"keyword\":\"Java\"}"),
                new AiChatTurnResponse(null, List.of(new AiToolCall("s2", TerminalToolContract.TOOL_NAME,
                        "{\"outcome\":\"FOUND\",\"candidateIds\":[21],\"summary\":\"找到 1 个\"}")), "tool_calls", 1, 1),
                // 匹配：预览 → 提交报告
                tool("m1", "preview_job_match", "{\"jobId\":21}"),
                new AiChatTurnResponse(null, List.of(new AiToolCall("m2", MatchReportContract.TOOL_NAME,
                        """
                                {"outcome":"COMPLETED","rankedItems":[
                                  {"jobId":21,"rank":1,"matchPoints":["熟练 Spring Boot，可支撑订单系统开发"],
                                   "gaps":["岗位要求 Redis，简历未见证据"],"suggestions":["补充缓存实践"],
                                   "evidenceRefs":["S1"]}]}""")), "tool_calls", 1, 1)
        );
    }

    @Test
    void compositeTaskShouldProduceEvidenceBackedAnswer() {
        stubCompositeTurns();

        JobSeekWorkflow.JobSeekOutcome outcome = workflow.execute(
                new JobSeekWorkflow.JobSeekInput(USER_ID, 10L, PLAN_ID, "trace",
                        "结合我的简历，找上海 Java 岗位，选出最合适的 5 个，并告诉我需要补哪些能力", "{}"));

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        // 真实岗位字段 + 匹配点 + 缺口，均来自可信产物
        assertThat(outcome.answer()).contains("Java 后端开发").contains("A公司").contains("20-30K/月")
                .contains("匹配点").contains("待补能力").contains("Redis");
        // 不允许出现未执行的动作
        assertThat(outcome.answer()).doesNotContain("已投递").doesNotContain("已发送");
        // 三类产物都落库
        verify(artifactService, times(3)).save(any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void missingResumeShouldAskForUploadWithoutMatching() {
        when(resumeService.listUserResumes(USER_ID)).thenReturn(List.of());

        JobSeekWorkflow.JobSeekOutcome outcome = workflow.execute(
                new JobSeekWorkflow.JobSeekInput(USER_ID, 10L, PLAN_ID, "trace",
                        "结合我的简历，找上海 Java 岗位", "{}"));

        assertThat(outcome.status()).isEqualTo("NEED_INPUT");
        assertThat(outcome.question()).contains("上传");
        assertThat(outcome.missingFields()).contains("resume");
        verify(gateway, times(0)).chatTurn(anyString(), any(), any(), anyLong(), anyString());
    }

    @Test
    void cityChangeShouldReuseProfileButRerunSearchAndMatch() throws Exception {
        // 上一轮产物：画像（简历版本不变）+ 旧候选（旧条件版本，不再命中）
        ResumeProfile profile = new ResumeProfile(11L, "v1", "张三简历", "Java", false,
                List.of("Java"), List.of("电商订单模块"),
                List.of(new ResumeProfile.Evidence("熟悉 Spring Boot", "S1", "熟悉 Spring Boot")),
                List.of(), List.of(), "COMPLETED", null);
        AgentArtifact profileArtifact = new AgentArtifact();
        profileArtifact.setId(1L);
        profileArtifact.setContentJson(mapper.writeValueAsString(profile));
        when(artifactService.findLatest(any(), any(), eq(AgentArtifactService.TYPE_RESUME_PROFILE), any()))
                .thenReturn(profileArtifact);
        when(artifactService.findLatest(any(), any(), eq(AgentArtifactService.TYPE_JOB_CANDIDATES), any()))
                .thenReturn(null);

        // 新条件下返回杭州岗位（旧候选按版本失效，不复用）
        when(invoker.invoke(eq(JobSearchWorkflow.SEARCH_TOOL), any(), any()))
                .thenReturn(AgentToolExecutionResult.builder().success(true)
                        .dataJson("""
                                {"total":1,"page":1,"pageSize":5,"jobs":[
                                  {"jobId":22,"jobTitle":"Java 后端开发","companyName":"B公司","city":"杭州",
                                   "minSalary":22000,"maxSalary":33000,"skillKeywords":"Java,Spring Boot"}
                                ]}""").build());
        JobPosition hangzhouJob = new JobPosition();
        hangzhouJob.setId(22L);
        hangzhouJob.setJobTitle("Java 后端开发");
        hangzhouJob.setCity("杭州");
        hangzhouJob.setMinSalary(22000);
        hangzhouJob.setMaxSalary(33000);
        hangzhouJob.setSkillKeywords("Java,Spring Boot");
        when(positionService.getById(22L)).thenReturn(hangzhouJob);
        when(matchService.previewMatch(anyLong(), anyLong(), eq(22L), any())).thenReturn(new JobMatchVO());

        // 只应有搜索(2) + 匹配(2) 四轮模型调用；画像节点被复用
        when(gateway.chatTurn(anyString(), any(), any(AiChatTurnRequest.class), anyLong(), anyString())).thenReturn(
                tool("s1", "job_search", "{\"keyword\":\"Java\"}"),
                new AiChatTurnResponse(null, List.of(new AiToolCall("s2", TerminalToolContract.TOOL_NAME,
                        "{\"outcome\":\"FOUND\",\"candidateIds\":[22],\"summary\":\"ok\"}")), "tool_calls", 1, 1),
                tool("m1", "preview_job_match", "{\"jobId\":22}"),
                new AiChatTurnResponse(null, List.of(new AiToolCall("m2", MatchReportContract.TOOL_NAME,
                        "{\"outcome\":\"COMPLETED\",\"rankedItems\":[{\"jobId\":22,\"rank\":1,"
                                + "\"matchPoints\":[\"Spring Boot 匹配\"],\"gaps\":[],\"suggestions\":[]}]}")),
                        "tool_calls", 1, 1)
        );

        // 上一轮条件：上海；本轮明确改杭州 → 条件版本变化
        String previous = "{\"keyword\":\"Java\",\"city\":\"上海\",\"sources\":{},\"targetRole\":\"Java\"}";
        JobSeekWorkflow.JobSeekOutcome outcome = workflow.execute(
                new JobSeekWorkflow.JobSeekInput(USER_ID, 10L, PLAN_ID, "trace",
                        "改成杭州的 Java 岗位，再看看匹配", previous));

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        // 画像复用 → 模型调用只剩搜索 + 匹配（共 4 轮）
        verify(gateway, times(4)).chatTurn(anyString(), any(), any(), anyLong(), anyString());
    }
}
