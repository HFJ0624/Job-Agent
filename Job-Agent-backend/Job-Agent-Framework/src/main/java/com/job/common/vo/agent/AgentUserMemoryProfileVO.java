package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentUserMemoryProfile;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 用户长期记忆画像展示 VO
 * 日期: 2026/6/23
 */
@Data
public class AgentUserMemoryProfileVO {

    private Long id;

    private Long userId;

    private String profileSummary;

    private Integer memoryCount;

    private Integer profileVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastBuildTime;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AgentUserMemoryProfileVO from(AgentUserMemoryProfile profile) {
        if (profile == null) {
            return null;
        }

        AgentUserMemoryProfileVO vo = new AgentUserMemoryProfileVO();
        vo.setId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setProfileSummary(profile.getProfileSummary());
        vo.setMemoryCount(profile.getMemoryCount());
        vo.setProfileVersion(profile.getProfileVersion());
        vo.setLastBuildTime(profile.getLastBuildTime());
        vo.setStatus(profile.getStatus());
        vo.setCreateTime(profile.getCreateTime());
        vo.setUpdateTime(profile.getUpdateTime());
        return vo;
    }
}
