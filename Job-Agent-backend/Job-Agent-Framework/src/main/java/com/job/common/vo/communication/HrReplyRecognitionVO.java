package com.job.common.vo.communication;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * HR 回复识别展示 VO。
 */
@Data
public class HrReplyRecognitionVO {

    private Long id;
    private Long userId;
    private Long applicationId;
    private Long communicationId;
    private Long jobId;
    private Long resumeId;
    private String companyName;
    private String jobTitle;
    private String currentStatus;
    private String hrReplyText;
    private String intentType;
    private String intentTypeDesc;
    private BigDecimal confidence;
    private String suggestedStatus;
    private String suggestedStatusDesc;
    private String communicationStatus;
    private Date interviewTime;
    private Date nextFollowTime;
    private List<String> todoItems;
    private String replySuggestion;
    private String reason;
    private String recognitionJson;
    private String confirmStatus;
    private String executedActionsJson;
    private String errorMsg;
    private Map<String, Boolean> defaultActions;
    private Date createTime;
    private Date updateTime;
}
