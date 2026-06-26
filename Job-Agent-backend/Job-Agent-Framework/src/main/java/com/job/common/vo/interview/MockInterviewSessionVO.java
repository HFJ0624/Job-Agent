package com.job.common.vo.interview;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.interview.MockInterviewSession;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 作者:hfj
 * 功能:模拟面试会话 VO
 */
@Data
public class MockInterviewSessionVO {

    private Long id;
    private Long applicationId;
    private Long interviewPrepareId;
    private Long jobId;
    private Long resumeId;

    private String jobTitle;
    private String companyName;
    private String status;

    private Integer currentIndex;
    private Integer totalQuestionCount;
    private BigDecimal totalScore;
    private String summary;

    private List<MockInterviewQuestionVO> questions;
    private List<MockInterviewAnswerVO> answers;
    private List<MockInterviewMediaRecordVO> mediaRecords;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Entity 转 VO。
     */
    public static MockInterviewSessionVO from(MockInterviewSession entity) {
        if (entity == null) {
            return null;
        }

        MockInterviewSessionVO vo = new MockInterviewSessionVO();
        vo.setId(entity.getId());
        vo.setApplicationId(entity.getApplicationId());
        vo.setInterviewPrepareId(entity.getInterviewPrepareId());
        vo.setJobId(entity.getJobId());
        vo.setResumeId(entity.getResumeId());
        vo.setJobTitle(entity.getJobTitle());
        vo.setCompanyName(entity.getCompanyName());
        vo.setStatus(entity.getStatus());
        vo.setCurrentIndex(entity.getCurrentIndex());
        vo.setTotalQuestionCount(entity.getTotalQuestionCount());
        vo.setTotalScore(entity.getTotalScore());
        vo.setSummary(entity.getSummary());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
