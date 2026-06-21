package com.job.common.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:AI Prompt 版本实体
 * 日期:2026/6/21
 *
 * 说明:
 * 1. Prompt 内容放在版本表，方便草稿、发布、回滚和 A/B。
 * 2. variablesJson 用于后台展示变量说明，第一版渲染时按 {{变量名}} 做替换。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_prompt_version")
public class AiPromptVersion extends BaseEntity {

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
}
