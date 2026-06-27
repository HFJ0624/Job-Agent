package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 模拟面试题库实体。
 *
 * 说明:
 * 1. 这张表保存 admin 可管理的结构化面试题和标准答案。
 * 2. ragDocumentId/ragChunkId 记录题目同步到 RAG 后的可视化入口，方便从题库追溯到知识切片。
 * 3. sourceHash 用于导入时去重，避免同一个 markdown 反复导入产生重复题目。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_question_bank")
public class InterviewQuestionBank extends BaseEntity {

    private String questionTitle;

    private String standardAnswer;

    private String questionType;

    private String category;

    private String difficulty;

    private String tags;

    private String sourceFile;

    private String sourceHash;

    private Long ragDocumentId;

    private Long ragChunkId;

    private String status;
}
