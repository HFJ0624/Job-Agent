package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;

/**
 * 作者:hfj
 * 功能: Agent 评测用例实体
 * 设计说明:
 * 1. 一个 Agent Eval Case 对应一条用户输入。
 * 2. expectedToolName 用来判断 Agent 是否调用了正确工具。
 * 3. expectedAnswerKeywords 用来判断最终回答是否包含关键内容。
 * 4. 这个表不是业务表，而是质量保障表，类似自动化测试用例。
 * 日期: 2026/6/9 16:32
 */
@Data
@TableName("agent_eval_case")
public class AgentEvalCase extends BaseEntity {

    /**
     * 测试用例名称。
     * 例如：岗位匹配-正常调用 JobMatchTool。
     */
    private String caseName;

    /**
     * 测试用户ID。
     * 注意：这个用户需要提前准备好简历、岗位、投递记录等测试数据。
     */
    private Long userId;

    /**
     * 用户输入。
     * 例如：帮我分析 resumeId=1 和 jobId=2 是否匹配。
     */
    private String inputMessage;

    /**
     * 期望意图。
     * 例如：JOB_MATCH、RESUME_ANALYZE、JOB_RECOMMEND。
     */
    private String expectedIntent;

    /**
     * 期望调用的工具名称。
     * 例如：JobMatchTool。
     */
    private String expectedToolName;

    /**
     * 期望回答包含的关键词。
     * 多个关键词用英文逗号分隔。
     */
    private String expectedAnswerKeywords;

    /**
     * 是否启用。
     */
    private Integer enableStatus;

    /**
     * 备注。
     */
    private String remark;
}
