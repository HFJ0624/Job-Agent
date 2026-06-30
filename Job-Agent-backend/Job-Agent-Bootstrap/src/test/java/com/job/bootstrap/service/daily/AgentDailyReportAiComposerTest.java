package com.job.bootstrap.service.daily;

import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.impl.AgentDailyReportAiComposer;
import com.job.common.vo.agent.AgentInboxVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agent AI 日报生成测试。
 *
 * 说明：
 * 1. 第二版要求模型失败时直接失败，不允许退回规则日报。
 * 2. 这里用假的模型网关验证 JSON 解析和失败传播，不依赖真实模型服务。
 */
class AgentDailyReportAiComposerTest {

    @Test
    void shouldParseAiJsonReport() {
        AiModelGatewayService gateway = (sceneCode, variables, userMessage, userId, traceId) -> """
                {
                  "title": "今日求职重点",
                  "summary": "今天先确认 HR 回复，再复习 Redis 错题。",
                  "todayFocus": ["确认 HR 回复", "复习 Redis 错题"],
                  "riskAlerts": ["面试时间临近"],
                  "recommendedOrder": ["先确认 HR 动作", "再完成学习计划"],
                  "topActions": ["确认面试时间", "复习项目亮点", "整理追问问题"]
                }
                """;

        AgentDailyReportAiComposer composer = new AgentDailyReportAiComposer(gateway);
        AgentDailyReportAiComposer.AiComposeResult result = composer.compose(1L, inboxWithOneItem());

        assertThat(result.title()).isEqualTo("今日求职重点");
        assertThat(result.summary()).contains("HR 回复");
        assertThat(result.content()).contains("今天最该做的 3 件事");
        assertThat(result.contentJson()).contains("recommendedOrder");
    }

    @Test
    void shouldFailWhenModelReturnsInvalidJson() {
        AiModelGatewayService gateway = (sceneCode, variables, userMessage, userId, traceId) -> "不是 JSON";
        AgentDailyReportAiComposer composer = new AgentDailyReportAiComposer(gateway);

        assertThatThrownBy(() -> composer.compose(1L, inboxWithOneItem()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI 日报 JSON 解析失败");
    }

    private AgentInboxVO inboxWithOneItem() {
        AgentInboxVO inbox = new AgentInboxVO();
        inbox.setTotalCount(1);
        inbox.setHighPriorityCount(1);
        inbox.setDueCount(1);
        inbox.setSummaryText("今天有 1 个高优先级待办。");

        AgentInboxVO.Item item = new AgentInboxVO.Item();
        item.setItemKey("HR_REPLY_CONFIRM_1");
        item.setItemType("HR_REPLY_CONFIRM");
        item.setItemTypeDesc("HR 回复待确认");
        item.setPriority("HIGH");
        item.setTitle("确认 HR 回复动作");
        item.setDescription("HR 邀请你明天下午面试");
        item.setTargetPath("/communication");
        inbox.setItems(List.of(item));
        return inbox;
    }
}
