package com.job.common.vo.interview;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.interview.MockInterviewMediaRecord;
import lombok.Data;

import java.util.Date;

/**
 * 功能: 模拟面试音视频记录展示对象。
 */
@Data
public class MockInterviewMediaRecordVO {

    private Long id;
    private Long sessionId;
    private Long questionId;
    private Long answerId;
    private Long userId;
    private String mediaType;
    private String fileUrl;
    private String objectName;
    private String fileName;
    private Long fileSize;
    private Integer durationSeconds;
    private String asrText;
    private String asrProvider;
    private String asrStatus;
    private String asrError;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static MockInterviewMediaRecordVO from(MockInterviewMediaRecord entity) {
        if (entity == null) {
            return null;
        }
        MockInterviewMediaRecordVO vo = new MockInterviewMediaRecordVO();
        vo.setId(entity.getId());
        vo.setSessionId(entity.getSessionId());
        vo.setQuestionId(entity.getQuestionId());
        vo.setAnswerId(entity.getAnswerId());
        vo.setUserId(entity.getUserId());
        vo.setMediaType(entity.getMediaType());
        vo.setFileUrl(entity.getFileUrl());
        vo.setObjectName(entity.getObjectName());
        vo.setFileName(entity.getFileName());
        vo.setFileSize(entity.getFileSize());
        vo.setDurationSeconds(entity.getDurationSeconds());
        vo.setAsrText(entity.getAsrText());
        vo.setAsrProvider(entity.getAsrProvider());
        vo.setAsrStatus(entity.getAsrStatus());
        vo.setAsrError(entity.getAsrError());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
