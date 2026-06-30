package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.vo.agent.AgentInboxVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 日报内容组装器。
 *
 * 设计说明：
 * 1. 第一版不调用大模型，直接基于 Agent Inbox 规则结果生成日报，保证定时任务稳定、低成本。
 * 2. 内容组装和数据库写入拆开，后续要替换成 LLM 生成时，只需要替换这个类的实现。
 * 3. 同时输出纯文本和结构化 JSON，纯文本用于邮件，JSON 用于后续前端做更丰富的卡片展示。
 */
@Component
public class AgentDailyReportComposer {

    private static final int MAX_RECOMMENDED_ITEMS = 5;
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final ObjectMapper objectMapper;

    public AgentDailyReportComposer() {
        this(new ObjectMapper());
    }

    public AgentDailyReportComposer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 根据 Inbox 聚合结果生成日报内容。
     *
     * @param inbox Agent Inbox 今日聚合结果
     * @return 日报标题、摘要、正文和结构化 JSON
     */
    public ComposeResult compose(AgentInboxVO inbox) {
        AgentInboxVO safeInbox = inbox == null ? new AgentInboxVO() : inbox;
        List<AgentInboxVO.Item> items = safeInbox.getItems() == null ? List.of() : safeInbox.getItems();

        /*
         * 1. 先生成摘要。摘要必须短，适合页面卡片和邮件开头展示。
         */
        String summary = buildSummary(safeInbox);

        /*
         * 2. 再生成建议处理顺序。这里只截取前 5 条，避免日报正文太长。
         */
        List<AgentInboxVO.Item> recommendedItems = items.stream()
                .limit(MAX_RECOMMENDED_ITEMS)
                .toList();

        /*
         * 3. 组装纯文本正文。纯文本兼容所有邮件客户端，也方便用户复制。
         */
        String content = buildContent(summary, safeInbox, recommendedItems);

        /*
         * 4. 组装结构化 JSON。第一版页面暂不强依赖，但先落库为后续可视化日报做准备。
         */
        String contentJson = buildContentJson(summary, safeInbox, recommendedItems);

        return new ComposeResult("今日求职 Agent 日报", summary, content, contentJson);
    }

    private String buildSummary(AgentInboxVO inbox) {
        Integer totalCount = defaultInt(inbox.getTotalCount());
        Integer highPriorityCount = defaultInt(inbox.getHighPriorityCount());
        Integer dueCount = defaultInt(inbox.getDueCount());

        if (totalCount == 0) {
            return "今天暂时没有必须处理的 Agent 待办，可以继续浏览岗位、优化简历或练习面试。";
        }
        return "今天有 " + totalCount + " 个待办，其中 "
                + highPriorityCount + " 个高优先级，"
                + dueCount + " 个已到期或需要今天处理。";
    }

    private String buildContent(String summary, AgentInboxVO inbox, List<AgentInboxVO.Item> recommendedItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("你好，这是你的今日求职 Agent 日报。\n\n");
        builder.append("【今日概览】\n");
        builder.append(summary).append("\n\n");
        builder.append("待办总数：").append(defaultInt(inbox.getTotalCount())).append("\n");
        builder.append("高优先级：").append(defaultInt(inbox.getHighPriorityCount())).append("\n");
        builder.append("已到期：").append(defaultInt(inbox.getDueCount())).append("\n\n");

        if (recommendedItems.isEmpty()) {
            builder.append("【建议处理顺序】\n");
            builder.append("今天没有强提醒事项，可以优先做简历优化、岗位筛选或模拟面试练习。\n");
            return builder.toString();
        }

        builder.append("【建议处理顺序】\n");
        for (int index = 0; index < recommendedItems.size(); index++) {
            AgentInboxVO.Item item = recommendedItems.get(index);
            builder.append(index + 1)
                    .append(". ")
                    .append(safe(item.getTitle(), "未命名待办"))
                    .append("（")
                    .append(priorityText(item.getPriority()))
                    .append("）\n");
            if (StringUtils.hasText(item.getDescription())) {
                builder.append("   说明：").append(item.getDescription().trim()).append("\n");
            }
            if (item.getDueTime() != null) {
                builder.append("   时间：").append(DATE_TIME_FORMAT.format(item.getDueTime())).append("\n");
            }
            if (StringUtils.hasText(item.getTargetPath())) {
                builder.append("   入口：").append(item.getTargetPath()).append("\n");
            }
        }

        builder.append("\n建议你先处理高优先级和已到期事项，再进入学习计划或错题复习。\n");
        return builder.toString();
    }

    private String buildContentJson(String summary, AgentInboxVO inbox, List<AgentInboxVO.Item> recommendedItems) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summary", summary);
        root.put("totalCount", defaultInt(inbox.getTotalCount()));
        root.put("highPriorityCount", defaultInt(inbox.getHighPriorityCount()));
        root.put("dueCount", defaultInt(inbox.getDueCount()));

        List<Map<String, Object>> itemList = new ArrayList<>();
        for (AgentInboxVO.Item item : recommendedItems) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("itemKey", item.getItemKey());
            itemMap.put("itemType", item.getItemType());
            itemMap.put("priority", item.getPriority());
            itemMap.put("title", item.getTitle());
            itemMap.put("description", item.getDescription());
            itemMap.put("targetPath", item.getTargetPath());
            itemMap.put("dueTime", item.getDueTime());
            itemList.add(itemMap);
        }
        root.put("recommendedItems", itemList);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String priorityText(String priority) {
        if ("HIGH".equals(priority)) {
            return "高优先级";
        }
        if ("LOW".equals(priority)) {
            return "低优先级";
        }
        return "普通";
    }

    private String safe(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 日报组装结果。
     */
    public record ComposeResult(
            String title,
            String summary,
            String content,
            String contentJson
    ) {
    }
}
