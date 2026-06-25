package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:Agent Eval 数据集实体，用来把同一类评测用例分组管理
 * 日期:2026/6/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_eval_dataset")
public class AgentEvalDataset extends BaseEntity {

    /**
     * 数据集名称。
     */
    private String datasetName;

    /**
     * 数据集编码，便于脚本或固定场景引用。
     */
    private String datasetCode;

    /**
     * 数据集说明。
     */
    private String description;

    /**
     * 默认评测类型。
     */
    private String evalType;

    /**
     * 是否启用：0禁用，1启用。
     */
    private Integer enableStatus;

    /**
     * 备注。
     */
    private String remark;
}
