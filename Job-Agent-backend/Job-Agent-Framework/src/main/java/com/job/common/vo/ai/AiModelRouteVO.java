package com.job.common.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.ai.AiModelRoute;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:AI 模型路由展示 VO
 * 日期:2026/6/21
 */
@Data
public class AiModelRouteVO {

    private Long id;

    private String sceneCode;

    private String routeName;

    private String primaryModelCode;

    private String fallbackModelCode;

    private String promptCode;

    private Long promptVersionId;

    private Integer grayPercent;

    private String abGroup;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AiModelRouteVO from(AiModelRoute entity) {
        if (entity == null) {
            return null;
        }

        AiModelRouteVO vo = new AiModelRouteVO();
        vo.setId(entity.getId());
        vo.setSceneCode(entity.getSceneCode());
        vo.setRouteName(entity.getRouteName());
        vo.setPrimaryModelCode(entity.getPrimaryModelCode());
        vo.setFallbackModelCode(entity.getFallbackModelCode());
        vo.setPromptCode(entity.getPromptCode());
        vo.setPromptVersionId(entity.getPromptVersionId());
        vo.setGrayPercent(entity.getGrayPercent());
        vo.setAbGroup(entity.getAbGroup());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
