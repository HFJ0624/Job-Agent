package com.job.bootstrap.service.action;

import com.job.bootstrap.service.impl.AgentActionItemFactory;
import com.job.common.entity.agent.AgentActionItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 行动项工厂测试。
 *
 * 说明：
 * 1. V1 不直接联动原业务表，只把 Agent 建议转成可确认、可追踪的行动项。
 * 2. AI 日报 topActions 是行动项的第一批来源，因此先验证它的转换规则。
 */
class AgentActionItemFactoryTest {

    @Test
    void shouldBuildActionItemsFromDailyReportTopActions() {
        AgentActionItemFactory factory = new AgentActionItemFactory();

        List<AgentActionItem> items = factory.fromDailyReportTopActions(
                7L,
                11L,
                List.of("确认 HR 面试时间", "复习 Redis 错题", "整理项目亮点")
        );

        assertThat(items).hasSize(3);
        assertThat(items.get(0).getUserId()).isEqualTo(7L);
        assertThat(items.get(0).getSourceType()).isEqualTo("DAILY_REPORT");
        assertThat(items.get(0).getSourceId()).isEqualTo(11L);
        assertThat(items.get(0).getActionTitle()).isEqualTo("确认 HR 面试时间");
        assertThat(items.get(0).getActionStatus()).isEqualTo("PENDING");
        assertThat(items.get(0).getTargetPath()).isEqualTo("/agent-inbox");
        assertThat(items.get(0).getActionKey()).isEqualTo("DAILY_REPORT_11_1");
    }

    @Test
    void shouldBuildExecutableActionItemsFromDailyReportExecutableActions() {
        AgentActionItemFactory factory = new AgentActionItemFactory();
        AgentActionItemFactory.ExecutableActionSpec spec = new AgentActionItemFactory.ExecutableActionSpec();
        spec.setActionTitle("发送面试通知邮件");
        spec.setActionDesc("确认后创建邮件通知工作流任务");
        spec.setActionType("WORKFLOW_TASK_CREATE");
        spec.setBizType("WORKFLOW_TASK");
        spec.setBizId(123L);
        spec.setActionPayload("{\"taskType\":\"INTERVIEW_EMAIL_NOTIFY\",\"bizId\":123}");
        spec.setPriority("HIGH");

        List<AgentActionItem> items = factory.fromDailyReportExecutableActions(7L, 11L, List.of(spec));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getActionKey()).isEqualTo("DAILY_REPORT_11_EXEC_1");
        assertThat(items.get(0).getActionType()).isEqualTo("WORKFLOW_TASK_CREATE");
        assertThat(items.get(0).getBizId()).isEqualTo(123L);
        assertThat(items.get(0).getActionPayload()).contains("INTERVIEW_EMAIL_NOTIFY");
    }
}
