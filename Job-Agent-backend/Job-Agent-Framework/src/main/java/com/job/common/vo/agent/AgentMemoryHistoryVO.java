package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentMemoryHistory;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 长期记忆版本历史 VO
 * 日期: 2026/6/25
 */
@Data
public class AgentMemoryHistoryVO {

    private Long id;

    private Long memoryId;

    private Long userId;

    private String memoryType;

    private String memoryKey;

    private String changeType;

    private String oldMemoryValue;

    private String newMemoryValue;

    private String oldSummary;

    private String newSummary;

    private String oldStatus;

    private String newStatus;

    private Integer conflictDetected;

    private String conflictReason;

    private String sourceType;

    private Long sourceId;

    private String operatorType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static AgentMemoryHistoryVO from(AgentMemoryHistory history) {
        if (history == null) {
            return null;
        }

        AgentMemoryHistoryVO vo = new AgentMemoryHistoryVO();
        vo.setId(history.getId());
        vo.setMemoryId(history.getMemoryId());
        vo.setUserId(history.getUserId());
        vo.setMemoryType(history.getMemoryType());
        vo.setMemoryKey(history.getMemoryKey());
        vo.setChangeType(history.getChangeType());
        vo.setOldMemoryValue(history.getOldMemoryValue());
        vo.setNewMemoryValue(history.getNewMemoryValue());
        vo.setOldSummary(history.getOldSummary());
        vo.setNewSummary(history.getNewSummary());
        vo.setOldStatus(history.getOldStatus());
        vo.setNewStatus(history.getNewStatus());
        vo.setConflictDetected(history.getConflictDetected());
        vo.setConflictReason(history.getConflictReason());
        vo.setSourceType(history.getSourceType());
        vo.setSourceId(history.getSourceId());
        vo.setOperatorType(history.getOperatorType());
        vo.setCreateTime(history.getCreateTime());
        return vo;
    }
}
