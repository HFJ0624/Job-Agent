package com.job.common.entity.decision;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 功能: AI 投递决策记录。
 *
 * 说明:
 * 1. 岗位匹配回答“像不像”，投递决策回答“要不要投”。
 * 2. 记录模型输出和 traceId，方便后续回看、审计和优化 Prompt。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_apply_decision_record")
public class JobApplyDecisionRecord extends BaseEntity {

    private Long userId;

    private Long resumeId;

    private Long jobId;

    private String jobTitle;

    private String companyName;

    private String decision;

    private String decisionLabel;

    private BigDecimal decisionScore;

    private String reason;

    private String risksJson;

    private String resumeSuggestionsJson;

    private String interviewSuggestionsJson;

    private String nextActionsJson;

    private Long matchRecordId;

    private String modelTraceId;

    private String rawResponse;

    private String source;
}
