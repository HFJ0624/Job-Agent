package com.job.bootstrap.agent.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.service.AiModelGatewayService;
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
 * AgentPlanAugmenter 单元测试（任务5 阶段三）。
 *
 * 覆盖重点:
 * 1. 合法补步（白名单工具 + 入参合法）→ 接收，toolName 归一为标准名。
 * 2. 非白名单工具 → 丢弃。
 * 3. 入参 key 不在该工具入参内 → 丢弃。
 * 4. 数量上限（最多 3 条）。
 * 5. ```json 围栏能解析；网关失败降级为空；空目标不调网关。
 */
class AgentPlanAugmenterTest {

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final AgentToolSchemaRegistry registry = new AgentToolSchemaRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentPlanAugmenter augmenter = new AgentPlanAugmenter(gateway, registry, objectMapper);

    @Test
    void validStepShouldBeAccepted() {
        when(gateway.chat(eq("PLAN_AUGMENT"), any(), anyString(), anyLong(), any()))
                .thenReturn("[{\"stepName\":\"搜索岗位\",\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"Java\"},\"completionCriteria\":\"返回岗位列表\"}]");

        List<AgentPlanAugmenter.AugmentedStep> steps = augmenter.augment(1L, "trace", "GENERAL_CHAT", "帮我找Java岗位");

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).toolName()).isEqualTo("JobSearchTool.searchJobs");
        assertThat(steps.get(0).toolInput()).containsEntry("keyword", "Java");
    }

    @Test
    void nonWhitelistedToolShouldBeDropped() {
        when(gateway.chat(eq("PLAN_AUGMENT"), any(), anyString(), anyLong(), any()))
                .thenReturn("[{\"stepName\":\"x\",\"toolName\":\"EvilTool.hack\",\"toolInput\":{}}]");

        assertThat(augmenter.augment(1L, "trace", "GENERAL_CHAT", "goal")).isEmpty();
    }

    @Test
    void illegalInputKeyShouldBeDropped() {
        when(gateway.chat(eq("PLAN_AUGMENT"), any(), anyString(), anyLong(), any()))
                .thenReturn("[{\"stepName\":\"x\",\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"Java\",\"evil\":\"x\"}}]");

        assertThat(augmenter.augment(1L, "trace", "GENERAL_CHAT", "goal")).isEmpty();
    }

    @Test
    void shouldCapAtMaxSteps() {
        when(gateway.chat(eq("PLAN_AUGMENT"), any(), anyString(), anyLong(), any()))
                .thenReturn("[" +
                        "{\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"a\"}}," +
                        "{\"toolName\":\"JobRecommendTool.recommendJobs\",\"toolInput\":{\"keyword\":\"b\"}}," +
                        "{\"toolName\":\"RagSearchTool.searchKnowledge\",\"toolInput\":{\"query\":\"c\"}}," +
                        "{\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"d\"}}," +
                        "{\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"e\"}}" +
                        "]");

        assertThat(augmenter.augment(1L, "trace", "GENERAL_CHAT", "goal")).hasSize(3);
    }

    @Test
    void fencedJsonShouldBeParsed() {
        when(gateway.chat(eq("PLAN_AUGMENT"), any(), anyString(), anyLong(), any()))
                .thenReturn("```json\n[{\"toolName\":\"JobSearchTool.searchJobs\",\"toolInput\":{\"keyword\":\"Java\"}}]\n```");

        assertThat(augmenter.augment(1L, "trace", "GENERAL_CHAT", "goal")).hasSize(1);
    }

    @Test
    void gatewayFailureShouldDegradeToEmpty() {
        when(gateway.chat(eq("PLAN_AUGMENT"), any(), anyString(), anyLong(), any()))
                .thenThrow(new RuntimeException("场景未配置"));
        assertThat(augmenter.augment(1L, "trace", "GENERAL_CHAT", "goal")).isEmpty();
    }

    @Test
    void blankGoalShouldNotCallGateway() {
        assertThat(augmenter.augment(1L, "trace", "GENERAL_CHAT", "  ")).isEmpty();
        verifyNoInteractions(gateway);
    }
}
