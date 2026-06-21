package com.job.common.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.ai.AiPromptVersion;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:AI Prompt 版本展示 VO
 * 日期:2026/6/21
 */
@Data
public class AiPromptVersionVO {

    private Long id;

    private Long templateId;

    private String versionNo;

    private String title;

    private String content;

    private String variablesJson;

    private String status;

    private Integer grayPercent;

    private String abGroup;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AiPromptVersionVO from(AiPromptVersion entity) {
        if (entity == null) {
            return null;
        }

        AiPromptVersionVO vo = new AiPromptVersionVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setVersionNo(entity.getVersionNo());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setVariablesJson(entity.getVariablesJson());
        vo.setStatus(entity.getStatus());
        vo.setGrayPercent(entity.getGrayPercent());
        vo.setAbGroup(entity.getAbGroup());
        vo.setPublishTime(entity.getPublishTime());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
