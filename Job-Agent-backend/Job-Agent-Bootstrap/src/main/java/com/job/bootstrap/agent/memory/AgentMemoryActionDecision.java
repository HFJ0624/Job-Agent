package com.job.bootstrap.agent.memory;

import com.job.enums.AgentMemoryActionType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 作者: hfj
 * 功能: 长期记忆动作判断结果
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. actionType 表示当前用户输入对记忆库的动作意图。
 * 2. targetMemoryKeys 只在 DELETE_MEMORY 场景下使用，用来限定要归档哪些记忆槽位。
 * 3. reason 只用于日志和后续排查，不参与业务判断。
 */
@Data
@Builder
public class AgentMemoryActionDecision {

    /**
     * 用户这句话对应的记忆动作。
     */
    private AgentMemoryActionType actionType;

    /**
     * 目标记忆 key。
     */
    private List<String> targetMemoryKeys;

    /**
     * 判断原因，便于后续排查为什么写入或跳过。
     */
    private String reason;

    public static AgentMemoryActionDecision normal(String reason) {
        return AgentMemoryActionDecision.builder()
                .actionType(AgentMemoryActionType.NORMAL_CHAT)
                .targetMemoryKeys(List.of())
                .reason(reason)
                .build();
    }
}
