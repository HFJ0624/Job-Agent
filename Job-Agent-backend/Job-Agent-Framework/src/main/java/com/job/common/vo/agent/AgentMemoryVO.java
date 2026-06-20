package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentLongTermMemory;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆展示 VO
 * 日期:2026/6/20
 */
@Data
public class AgentMemoryVO {

    private Long id;

    private Long userId;

    private String memoryType;

    private String memoryKey;

    private String memoryValue;

    private String summary;

    private String sourceType;

    private Long sourceId;

    private BigDecimal confidence;

    private BigDecimal importance;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUsedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AgentMemoryVO from(AgentLongTermMemory memory) {
        if (memory == null) {
            return null;
        }

        AgentMemoryVO vo = new AgentMemoryVO();
        vo.setId(memory.getId());
        vo.setUserId(memory.getUserId());
        vo.setMemoryType(memory.getMemoryType());
        vo.setMemoryKey(memory.getMemoryKey());
        vo.setMemoryValue(memory.getMemoryValue());
        vo.setSummary(memory.getSummary());
        vo.setSourceType(memory.getSourceType());
        vo.setSourceId(memory.getSourceId());
        vo.setConfidence(memory.getConfidence());
        vo.setImportance(memory.getImportance());
        vo.setStatus(memory.getStatus());
        vo.setLastUsedTime(memory.getLastUsedTime());
        vo.setCreateTime(memory.getCreateTime());
        vo.setUpdateTime(memory.getUpdateTime());
        return vo;
    }
}
