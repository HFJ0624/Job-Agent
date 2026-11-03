package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:Agent 计划实体
 * 日期:2026/6/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_plan")
public class AgentPlan extends BaseEntity {

    /**
     * 本轮对话 TraceId。
     */
    private String traceId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 意图编码。
     */
    private String intentCode;

    /**
     * 用户原始目标。
     */
    private String userGoal;

    /**
     * 计划标题。
     */
    private String planTitle;

    /**
     * 计划摘要。
     */
    private String planSummary;

    /**
     * 必要参数 JSON。
     */
    private String requiredParamsJson;

    /**
     * 已抽取参数 JSON。
     */
    private String extractedParamsJson;

    /**
     * 缺失参数 JSON。
     */
    private String missingParamsJson;

    /**
     * 计划状态。
     */
    private String status;

    /**
     * 失败原因。
     */
    private String failReason;

    /**
     * 失败后重规划次数（任务 G2 Replan）。
     * 步骤失败且还有预算时，执行器会让 Replanner 给出替代步骤；该字段用于有界控制与审计。
     */
    private Integer replanCount;

    /**
     * 执行批次号（任务 J2）：每次成功抢占 +1，所有步骤回写带批次校验，
     * 旧执行器的写入影响 0 行，不会覆盖新执行器的状态。
     */
    private Integer executionEpoch;

    /**
     * 执行引擎（第二阶段）：WORKFLOW_MULTI_AGENT / WORKFLOW_REACT / TEMPLATE。
     * 创建时固定，恢复时优先按它路由——开关只影响新任务。
     */
    private String runtimeMode;

    /** 等待用户补充时的具体问题（第三阶段，前端刷新后据此恢复）。 */
    private String pendingQuestion;

    /** 用户显式取消时间（第三阶段）。 */
    private java.util.Date cancelledAt;

    /** 父任务已用模型请求次数（第三阶段，补参恢复不得清零）。 */
    private Integer modelCallsUsed;

    /** 父任务已用业务工具次数（第三阶段）。 */
    private Integer toolCallsUsed;

    /** 任务完成时的最终答复（第三阶段收口：重复提交直接复用）。 */
    private String finalAnswer;

    /** 已消耗执行耗时（毫秒；补参等待不计时，恢复时延续）。 */
    private Integer executionMillisUsed;
}
