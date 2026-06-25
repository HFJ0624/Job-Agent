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
     * 所属数据集ID。
     */
    private Long datasetId;

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
     * 评测类型：TOOL_CALL / RAG_RETRIEVAL / ANSWER_QUALITY / END_TO_END。
     */
    private String evalType;

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
     * 期望工具参数 JSON。
     * 说明: 第一版按“期望 JSON 是否被实际 JSON 包含”判断参数准确率。
     */
    private String expectedToolParamsJson;

    /**
     * 期望命中的 RAG 文档ID。
     */
    private Long expectedRagDocumentId;

    /**
     * 期望命中的 RAG 切片ID。
     */
    private Long expectedRagChunkId;

    /**
     * 期望 RAG 结果包含的关键词，多个关键词用英文逗号分隔。
     */
    private String expectedRagKeywords;

    /**
     * 期望回答包含的关键词。
     * 多个关键词用英文逗号分隔。
     */
    private String expectedAnswerKeywords;

    /**
     * 最低回答质量分。
     */
    private java.math.BigDecimal minAnswerScore;

    /**
     * 用例标签，多个标签用英文逗号分隔。
     */
    private String tags;

    /**
     * 是否启用。
     */
    private Integer enableStatus;

    /**
     * 备注。
     */
    private String remark;
}
