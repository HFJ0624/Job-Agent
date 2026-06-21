package com.job.common.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.ai.AiPromptTemplate;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:AI Prompt 模板展示 VO
 * 日期:2026/6/21
 */
@Data
public class AiPromptTemplateVO {

    private Long id;

    private String promptCode;

    private String promptName;

    private String sceneCode;

    private String description;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AiPromptTemplateVO from(AiPromptTemplate entity) {
        if (entity == null) {
            return null;
        }

        AiPromptTemplateVO vo = new AiPromptTemplateVO();
        vo.setId(entity.getId());
        vo.setPromptCode(entity.getPromptCode());
        vo.setPromptName(entity.getPromptName());
        vo.setSceneCode(entity.getSceneCode());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
