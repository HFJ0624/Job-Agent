package com.job.common.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:AI Prompt 模板实体
 * 日期:2026/6/21
 *
 * 说明:
 * 1. Prompt 模板表示一个业务场景，例如 AGENT_SUMMARY。
 * 2. 一个模板下面可以有多个版本，管理员可以发布不同版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_prompt_template")
public class AiPromptTemplate extends BaseEntity {

    private String promptCode;

    private String promptName;

    private String sceneCode;

    private String description;

    private String status;
}
