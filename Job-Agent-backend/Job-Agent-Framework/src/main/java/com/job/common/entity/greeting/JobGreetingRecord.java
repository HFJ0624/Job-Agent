package com.job.common.entity.greeting;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:HR 打招呼语生成记录实体
 * 说明:
 * 1. 每次用户生成打招呼语，都保存一条记录。
 * 2. 后续可以用于历史记录、用户行为分析、Agent Trace 或 Prompt 优化。
 * 日期: 2026/6/8 13:56
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_greeting_record")
public class JobGreetingRecord extends BaseEntity {

    /**
     * 当前登录用户ID。
     */
    private Long userId;

    /**
     * 本次用于生成话术的简历ID。
     */
    private Long resumeId;

    /**
     * 本次生成话术对应的岗位ID。
     */
    private Long jobId;

    /**
     * 话术风格。
     * 例如：正式、自然、自信、实习生风格、社招风格、简洁直达。
     */
    private String style;

    /**
     * 生成后的打招呼语正文。
     */
    private String content;

    /**
     * 生成时使用的匹配技能，JSON 数组字符串。
     */
    private String matchedSkills;

    /**
     * 生成来源。
     * 第一版先用 RULE，后续接大模型后可以改成 LLM。
     */
    private String source;
}
