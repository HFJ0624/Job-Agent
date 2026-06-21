package com.job.common.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:AI 模型熔断状态实体
 * 日期:2026/6/21
 *
 * 说明:
 * 1. 记录模型连续失败次数和熔断打开时间。
 * 2. 模型网关调用前会读取本表，OPEN 且未冷却完成时跳过该模型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_circuit_state")
public class AiModelCircuitState extends BaseEntity {

    private String modelCode;

    private Integer failureCount;

    private String circuitStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastFailureTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date openedUntil;
}
