package com.job.common.vo.interview;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.interview.InterviewQuestionBank;
import lombok.Data;

import java.util.Date;

/**
 * 后台面试题库展示对象。
 */
@Data
public class InterviewQuestionBankVO {

    private Long id;

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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static InterviewQuestionBankVO from(InterviewQuestionBank question) {
        if (question == null) {
            return null;
        }

        InterviewQuestionBankVO vo = new InterviewQuestionBankVO();
        vo.setId(question.getId());
        vo.setQuestionTitle(question.getQuestionTitle());
        vo.setStandardAnswer(question.getStandardAnswer());
        vo.setQuestionType(question.getQuestionType());
        vo.setCategory(question.getCategory());
        vo.setDifficulty(question.getDifficulty());
        vo.setTags(question.getTags());
        vo.setSourceFile(question.getSourceFile());
        vo.setSourceHash(question.getSourceHash());
        vo.setRagDocumentId(question.getRagDocumentId());
        vo.setRagChunkId(question.getRagChunkId());
        vo.setStatus(question.getStatus());
        vo.setCreateTime(question.getCreateTime());
        vo.setUpdateTime(question.getUpdateTime());
        return vo;
    }
}
