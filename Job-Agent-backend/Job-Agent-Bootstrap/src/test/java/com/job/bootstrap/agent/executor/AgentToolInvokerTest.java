package com.job.bootstrap.agent.executor;

import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.agent.tools.GreetingGenerateTool;
import com.job.bootstrap.agent.tools.InterviewPrepareTool;
import com.job.bootstrap.agent.tools.JobMatchTool;
import com.job.bootstrap.agent.tools.JobRecommendTool;
import com.job.bootstrap.agent.tools.JobSearchTool;
import com.job.bootstrap.agent.tools.MockInterviewReviewTool;
import com.job.bootstrap.agent.tools.RagSearchTool;
import com.job.bootstrap.agent.tools.ResumeAnalyzeTool;
import com.job.bootstrap.service.AdminExternalConnectorService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentToolInvoker 单元测试。
 *
 * 覆盖重点:
 * 1. 工具分发：switch 按 toolName 正确路由到对应工具方法，并透传解析后的参数。
 * 2. 未知工具名：返回 failed 结果，错误码 TOOL_NOT_REGISTERED，不调用任何业务工具。
 * 3. 输出校验失败：AgentGuardrailService.validateToolOutput 抛错时，结果转为 failed，
 *    错误码透传为具体的 TOOL_OUTPUT_* 错误。
 */
class AgentToolInvokerTest {

    private final ResumeAnalyzeTool resumeAnalyzeTool = mock(ResumeAnalyzeTool.class);
    private final JobMatchTool jobMatchTool = mock(JobMatchTool.class);
    private final GreetingGenerateTool greetingGenerateTool = mock(GreetingGenerateTool.class);
    private final JobSearchTool jobSearchTool = mock(JobSearchTool.class);
    private final JobRecommendTool jobRecommendTool = mock(JobRecommendTool.class);
    private final InterviewPrepareTool interviewPrepareTool = mock(InterviewPrepareTool.class);
    private final MockInterviewReviewTool mockInterviewReviewTool = mock(MockInterviewReviewTool.class);
    private final RagSearchTool ragSearchTool = mock(RagSearchTool.class);
    private final AgentToolSchemaRegistry agentToolSchemaRegistry = mock(AgentToolSchemaRegistry.class);
    private final AgentGuardrailService agentGuardrailService = mock(AgentGuardrailService.class);
    private final AdminExternalConnectorService adminExternalConnectorService = mock(AdminExternalConnectorService.class);

    private final AgentToolInvoker invoker = new AgentToolInvoker(
            resumeAnalyzeTool,
            jobMatchTool,
            greetingGenerateTool,
            jobSearchTool,
            jobRecommendTool,
            interviewPrepareTool,
            mockInterviewReviewTool,
            ragSearchTool,
            agentToolSchemaRegistry,
            agentGuardrailService,
            adminExternalConnectorService
    );

    @Test
    void shouldDispatchToResumeAnalyzeToolWithParsedParams() {
        Map<String, Object> params = Map.of(
                "resumeId", 1L,
                "resumeName", "张三简历",
                "targetPosition", "Java后端"
        );
        when(resumeAnalyzeTool.analyzeResume(1L, "张三简历", "Java后端")).thenReturn("{\"score\":86}");
        when(agentToolSchemaRegistry.getRequired("ResumeAnalyzeTool.analyzeResume"))
                .thenReturn(AgentToolSchema.builder().toolName("ResumeAnalyzeTool.analyzeResume").build());
        doNothing().when(agentGuardrailService).validateToolOutput(any(), any(), any());

        AgentToolExecutionResult result = invoker.invoke("ResumeAnalyzeTool.analyzeResume", params, "帮我分析简历");

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getToolName()).isEqualTo("ResumeAnalyzeTool.analyzeResume");
        assertThat(result.getDataJson()).isEqualTo("{\"score\":86}");
        verify(resumeAnalyzeTool).analyzeResume(1L, "张三简历", "Java后端");
        verify(agentGuardrailService).validateToolOutput(any(), any(), any());
    }

    @Test
    void shouldDispatchToGreetingGenerateToolWithStyleParam() {
        Map<String, Object> params = Map.of(
                "resumeId", 1L,
                "resumeName", "R",
                "jobId", 2L,
                "jobTitle", "J",
                "style", "正式"
        );
        when(greetingGenerateTool.generateGreeting(1L, "R", 2L, "J", "正式")).thenReturn("{\"greeting\":\"hi\"}");
        when(agentToolSchemaRegistry.getRequired("GreetingGenerateTool.generateGreeting"))
                .thenReturn(AgentToolSchema.builder().toolName("GreetingGenerateTool.generateGreeting").build());
        doNothing().when(agentGuardrailService).validateToolOutput(any(), any(), any());

        AgentToolExecutionResult result = invoker.invoke("GreetingGenerateTool.generateGreeting", params, "生成招呼语");

        assertThat(result.getSuccess()).isTrue();
        verify(greetingGenerateTool).generateGreeting(1L, "R", 2L, "J", "正式");
    }

    @Test
    void unknownToolShouldReturnFailedResultWithNotRegisteredError() {
        AgentToolExecutionResult result = invoker.invoke("NonExistentTool.doSomething", Map.of(), "随便说点什么");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(AgentToolErrorCode.TOOL_NOT_REGISTERED.name());
        verify(resumeAnalyzeTool, never()).analyzeResume(any(), any(), any());
        verify(greetingGenerateTool, never()).generateGreeting(any(), any(), any(), any(), any());
    }

    @Test
    void toolOutputValidationFailureShouldReturnFailedResult() {
        Map<String, Object> params = Map.of("resumeId", 1L, "resumeName", "R", "targetPosition", "P");
        when(resumeAnalyzeTool.analyzeResume(1L, "R", "P")).thenReturn("bad-json");
        when(agentToolSchemaRegistry.getRequired("ResumeAnalyzeTool.analyzeResume"))
                .thenReturn(AgentToolSchema.builder().toolName("ResumeAnalyzeTool.analyzeResume").build());
        doThrow(new AgentToolException(
                AgentToolErrorCode.TOOL_OUTPUT_INVALID_JSON,
                "ResumeAnalyzeTool.analyzeResume",
                "输出不是合法JSON"))
                .when(agentGuardrailService).validateToolOutput(any(), any(), any());

        AgentToolExecutionResult result = invoker.invoke("ResumeAnalyzeTool.analyzeResume", params, "分析简历");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(AgentToolErrorCode.TOOL_OUTPUT_INVALID_JSON.name());
        verify(agentGuardrailService).validateToolOutput(any(), any(), any());
    }
}
