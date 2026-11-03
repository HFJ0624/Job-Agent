package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 功能: Agent run 实体（第一阶段 ReAct + Workflow）。
 *
 * 设计说明:
 * 1. 复用现有计划/会话 ID（plan_id/conversation_id），不提前建多 Agent 平台；
 * 2. messages_json 保存完整消息序列（含 tool_calls 与 tool 结果）——恢复必须用它，
 *    不能用摘要（文档要求：大于 1000 字符的 JSON 必须完整保存）；
 * 3. attempt 每次领取 +1，写回时校验，防止旧执行器晚回写覆盖新结果；
 * 4. version 每次状态变更 +1，便于乐观并发与审计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_run")
public class AgentRun extends BaseEntity {

    /** 用户ID。 */
    private Long userId;

    /** 会话ID。 */
    private Long conversationId;

    /** 关联计划ID（可空）。 */
    private Long planId;

    /** 链路ID。 */
    private String traceId;

    /** Agent 名称，如 job-search。 */
    private String agentName;

    /** 状态：PENDING/RUNNING/WAITING_INPUT/SUCCEEDED/FAILED。 */
    private String status;

    /** 本轮用户输入。 */
    private String inputText;

    /** SearchConstraints 快照（含来源标记）。 */
    private String constraintsJson;

    /** 完整消息序列 JSON（含 tool_calls/tool 结果）。 */
    private String messagesJson;

    /** 最终结果 JSON（outcome/候选/缺失字段/原因码）。 */
    private String resultJson;

    /** 失败原因。 */
    private String errorMsg;

    /** 领取次数。 */
    private Integer attempt;

    /** 版本号。 */
    private Integer version;
}
