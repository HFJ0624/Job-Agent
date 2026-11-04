package com.job.bootstrap.agent.application.routing;

import com.job.bootstrap.agent.application.task.AgentTaskService;
import com.job.bootstrap.agent.scenario.jobsearch.JobSearchReactEntry;
import com.job.bootstrap.agent.scenario.jobseek.entry.JobSeekReactEntry;
import com.job.bootstrap.agent.scenario.resume.ResumeReactEntry;
import com.job.common.entity.agent.AgentPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 已有任务路由验证：planId 存在时严格按持久化 runtimeMode 选择处理器，
 * 不允许被消息里的"简历/岗位"等关键词抢走。
 */
class ChatRequestRouterTest {

    private final AgentTaskService taskService = mock(AgentTaskService.class);
    private final JobSeekReactEntry jobSeekEntry = mock(JobSeekReactEntry.class);
    private final ResumeReactEntry resumeEntry = mock(ResumeReactEntry.class);
    private final JobSearchReactEntry searchEntry = mock(JobSearchReactEntry.class);
    private final ChatRequestRouter router =
            new ChatRequestRouter(taskService, jobSeekEntry, resumeEntry, searchEntry);

    private AgentPlan plan(String runtimeMode) {
        AgentPlan plan = new AgentPlan();
        plan.setId(9L);
        plan.setUserId(1L);
        plan.setRuntimeMode(runtimeMode);
        return plan;
    }

    @Test
    void resumeTaskShouldStayOnResumeEvenWhenMessageMentionsJob() {
        when(taskService.load(9L)).thenReturn(plan(ResumeReactEntry.RUNTIME_RESUME));
        when(jobSeekEntry.shouldHandle("GENERAL_CHAT", null, "简历 岗位")).thenReturn(true);

        ChatRequestRouter.Route route = router.route("GENERAL_CHAT", 9L, "用后端简历继续找岗位");

        assertThat(route).isEqualTo(ChatRequestRouter.Route.RESUME);
        // 关键：没有去问复合入口，避免被关键词抢走
        verify(jobSeekEntry, never()).shouldHandle("GENERAL_CHAT", null, "用后端简历继续找岗位");
    }

    @Test
    void multiAgentTaskShouldStayOnMultiAgent() {
        when(taskService.load(9L)).thenReturn(plan(AgentTaskService.RUNTIME_MULTI_AGENT));
        assertThat(router.route("RESUME_ANALYZE", 9L, "继续"))
                .isEqualTo(ChatRequestRouter.Route.MULTI_AGENT);
    }

    @Test
    void newRequestShouldUseKeywordRouting() {
        when(jobSeekEntry.shouldHandle("JOB_SEARCH", null, "找上海 Java 岗位")).thenReturn(false);
        when(resumeEntry.shouldHandle("JOB_SEARCH", null, "找上海 Java 岗位")).thenReturn(false);
        when(searchEntry.shouldHandle("JOB_SEARCH", null)).thenReturn(true);

        assertThat(router.route("JOB_SEARCH", null, "找上海 Java 岗位"))
                .isEqualTo(ChatRequestRouter.Route.REACT_SEARCH);
    }

    @Test
    void unknownTaskShouldFallBackToLegacy() {
        when(taskService.load(404L)).thenReturn(null);
        assertThat(router.route("GENERAL_CHAT", 404L, "任意消息"))
                .isEqualTo(ChatRequestRouter.Route.LEGACY);
    }
}
