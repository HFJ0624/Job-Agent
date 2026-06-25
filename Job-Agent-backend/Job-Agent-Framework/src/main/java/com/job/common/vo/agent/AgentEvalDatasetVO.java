package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentEvalDataset;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:Agent Eval 数据集展示对象
 * 日期:2026/6/24
 */
@Data
public class AgentEvalDatasetVO {
    private Long id;
    private String datasetName;
    private String datasetCode;
    private String description;
    private String evalType;
    private Integer enableStatus;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AgentEvalDatasetVO from(AgentEvalDataset entity) {
        if (entity == null) {
            return null;
        }
        AgentEvalDatasetVO vo = new AgentEvalDatasetVO();
        vo.setId(entity.getId());
        vo.setDatasetName(entity.getDatasetName());
        vo.setDatasetCode(entity.getDatasetCode());
        vo.setDescription(entity.getDescription());
        vo.setEvalType(entity.getEvalType());
        vo.setEnableStatus(entity.getEnableStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
