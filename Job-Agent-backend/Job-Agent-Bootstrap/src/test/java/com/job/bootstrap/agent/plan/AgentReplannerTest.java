package com.job.bootstrap.agent.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AgentReplanner 单元测试（企业级对标 G2）。
 *
 * 覆盖重点（与 AgentPlanAugmenter 同款安全边界）:
 * 1. 合法替代步骤（白名单工具 + 入参合法）→ 接收，toolName 归一为标准名。
 * 2. 非白名单工具 / 非法入参 key → 丢弃。
 * 3. 数量上限（最多 3 条）。
 * 4. ```json 围栏能解析；网关失败降级为空；空目标不调网关。
 * 5. 走 PLAN_REPLAN 场景、STANDARD 档位（规划级决策）。
 */
class AgentReplannerTest {

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final AgentToolSchemaRegistry registry = new AgentToolSchemaRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentReplanner replanner = new AgentReplanner(gateway, registry, objectMapper);

    private void stubGateway(String output) {
        when(gateway.chat(eq("PLAN_REPLAN"), eq(AiModelTier.STANDARD), any(), anyString(), anyLong(), any()))
                .thenReturn(output);
    }

    @Test
    void validStepShouldBeAccepted() {
        stubGateway("[{\"stepName\":\"重新搜索岗位\",\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"Java\"},\"completionCriteria\":\"返回岗位列表\"}]");

        List<AgentReplanner.ReplanStep> steps = replanner.replan(
                1L, "trace", "帮我找Java岗位并投递", "步骤1（搜索岗位）: 已找到3个", "投递步骤", "缺少 jobId");

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).toolName()).isEqualTo("JobSearchTool.searchJobs");
        assertThat(steps.get(0).toolInput()).containsEntry("keyword", "Java");
    }

    @Test
    void nonWhitelistedToolShouldBeDropped() {
        stubGateway("[{\"stepName\":\"x\",\"toolName\":\"EvilTool.hack\",\"toolInput\":{}}]");

        assertThat(replanner.replan(1L, "trace", "goal", "", "步骤1", "失败")).isEmpty();
    }

    @Test
    void illegalInputKeyShouldBeDropped() {
        stubGateway("[{\"stepName\":\"x\",\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"Java\",\"evil\":\"x\"}}]");

        assertThat(replanner.replan(1L, "trace", "goal", "", "步骤1", "失败")).isEmpty();
    }

    @Test
    void shouldCapAtMaxSteps() {
        stubGateway("[" +
                "{\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"a\"}}," +
                "{\"toolName\":\"JobRecommendTool.recommendJobs\",\"toolInput\":{\"keyword\":\"b\"}}," +
                "{\"toolName\":\"RagSearchTool.searchKnowledge\",\"toolInput\":{\"query\":\"c\"}}," +
                "{\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"d\"}}" +
                "]");

        assertThat(replanner.replan(1L, "trace", "goal", "", "步骤1", "失败")).hasSize(3);
    }

    @Test
    void fencedJsonShouldBeParsed() {
        stubGateway("```json\n[{\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"Java\"}}]\n```");

        assertThat(replanner.replan(1L, "trace", "goal", "", "步骤1", "失败")).hasSize(1);
    }

    @Test
    void gatewayFailureShouldDegradeToEmpty() {
        when(gateway.chat(eq("PLAN_REPLAN"), eq(AiModelTier.STANDARD), any(), anyString(), anyLong(), any()))
                .thenThrow(new RuntimeException("场景未配置"));

        assertThat(replanner.replan(1L, "trace", "goal", "", "步骤1", "失败")).isEmpty();
    }

    @Test
    void blankGoalShouldNotCallGateway() {
        assertThat(replanner.replan(1L, "trace", "  ", "", "步骤1", "失败")).isEmpty();
        verifyNoInteractions(gateway);
    }
}
