package com.job.common.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:AI 模型路由实体
 * 日期:2026/6/21
 *
 * 说明:
 * 1. 路由把业务场景、Prompt、主模型、备用模型绑定起来。
 * 2. 运行时只传 sceneCode，就能找到当前应该使用的 Prompt 和模型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_route")
public class AiModelRoute extends BaseEntity {

    private String sceneCode;

    private String routeName;

    private String primaryModelCode;

    private String fallbackModelCode;

    private String promptCode;

    private Long promptVersionId;

    private Integer grayPercent;

    private String abGroup;

    private String status;
}
