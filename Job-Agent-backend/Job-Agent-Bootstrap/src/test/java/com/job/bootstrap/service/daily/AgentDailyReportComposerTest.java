package com.job.bootstrap.service.daily;

import com.job.bootstrap.service.impl.AgentDailyReportComposer;
import com.job.common.vo.agent.AgentInboxVO;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 主动日报内容组装测试。
 *
 * 说明：
 * 1. 日报第一版先不调用大模型，避免定时任务每天生成内容时产生不可控成本。
 * 2. 这里验证“从 Inbox 聚合结果生成日报正文”这一段核心规则，邮件发送和数据库写入交给服务层集成。
 */
class AgentDailyReportComposerTest {

    @Test
    void shouldComposeReadableReportFromInboxItems() {
        AgentInboxVO inbox = new AgentInboxVO();
        inbox.setTotalCount(2);
        inbox.setHighPriorityCount(1);
        inbox.setDueCount(1);
        inbox.setSummaryText("今天有 2 个 Agent 待办，其中 1 个高优先级。");
        inbox.setItems(List.of(
                item("HR_REPLY_CONFIRM_9", "HIGH", "确认 HR 回复动作：示例科技", "HR 提到明天下午三点面试", new Date()),
                item("LEARNING_PLAN_3", "NORMAL", "继续学习：Redis 高频题", "完成缓存穿透与缓存击穿复习", null)
        ));

        AgentDailyReportComposer composer = new AgentDailyReportComposer();
        AgentDailyReportComposer.ComposeResult result = composer.compose(inbox);

        assertThat(result.title()).isEqualTo("今日求职 Agent 日报");
        assertThat(result.summary()).contains("2 个待办").contains("1 个高优先级").contains("1 个已到期");
        assertThat(result.content()).contains("确认 HR 回复动作：示例科技");
        assertThat(result.content()).contains("建议处理顺序");
        assertThat(result.content()).contains("Redis 高频题");
    }

    private AgentInboxVO.Item item(String itemKey, String priority, String title, String description, Date dueTime) {
        AgentInboxVO.Item item = new AgentInboxVO.Item();
        item.setItemKey(itemKey);
        item.setItemType(itemKey.substring(0, itemKey.lastIndexOf("_")));
        item.setItemTypeDesc("测试待办");
        item.setPriority(priority);
        item.setTitle(title);
        item.setDescription(description);
        item.setActionText("去处理");
        item.setTargetPath("/agent-inbox");
        item.setDueTime(dueTime);
        item.setCreateTime(new Date());
        return item;
    }
}
