package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 功能: Agent 结构化产物（第二阶段）。
 *
 * 设计说明:
 * 1. 三个 Agent 之间只通过产物传递事实（ResumeProfile / JobCandidates / MatchReport），
 *    不互相修改状态、不拼接完整聊天记录；
 * 2. contentJson 保存完整 JSON——摘要只用于展示，不参与程序恢复；
 * 3. inputVersion 记录条件版本/上游产物版本：版本不一致即视为失效，避免旧产物被复用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_artifact")
public class AgentArtifact extends BaseEntity {

    /** 用户ID（归属校验）。 */
    private Long userId;

    /** 父计划ID。 */
    private Long planId;

    /** 产出该产物的子 run ID。 */
    private Long runId;

    /** 产物类型：RESUME_PROFILE / JOB_CANDIDATES / MATCH_REPORT。 */
    private String type;

    /** 产物 Schema 版本，如 v1。 */
    private String schemaVersion;

    /** 输入版本/条件版本。 */
    private String inputVersion;

    /** 完整产物 JSON。 */
    private String contentJson;

    /** 来源引用（简历/岗位/上游产物 ID）。 */
    private String sourceRefs;
}
