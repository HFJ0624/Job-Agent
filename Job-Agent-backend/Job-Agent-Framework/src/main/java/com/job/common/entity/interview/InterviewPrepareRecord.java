package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:AI 面试准备记录实体
 * 说明:
 * 1. 用户针对某条求职记录生成一次面试准备，会保存一条记录。
 * 2. 技术题、项目追问题、HR题、复习建议都用 JSON 字符串保存。
 * 3. 第一版可以使用规则生成，后续接入 LLM 后 source 改成 LLM。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_prepare_record")
public class InterviewPrepareRecord extends BaseEntity {

    /**
     * 当前登录用户ID。
     */
    private Long userId;

    /**
     * 求职记录ID。
     */
    private Long applicationId;

    /**
     * 岗位ID。
     */
    private Long jobId;

    /**
     * 简历ID。
     */
    private Long resumeId;

    /**
     * 岗位名称快照。
     */
    private String jobTitle;

    /**
     * 公司名称快照。
     */
    private String companyName;

    /**
     * 技术面试题 JSON 数组字符串。
     */
    private String technicalQuestions;

    /**
     * 项目追问题 JSON 数组字符串。
     */
    private String projectQuestions;

    /**
     * HR 面试题 JSON 数组字符串。
     */
    private String hrQuestions;

    /**
     * 复习建议 JSON 数组字符串。
     */
    private String reviewSuggestions;

    /**
     * 总结。
     */
    private String summary;

    /**
     * 生成来源：RULE / LLM。
     */
    private String source;
}
