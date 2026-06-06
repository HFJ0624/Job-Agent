package com.job.common.entity.interaction;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:岗位沟通消息实体类，对应数据库 job_position_message 表
 * 日期:2026/6/6 16:10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_position_message")
public class JobPositionMessage extends BaseEntity {

    /**
     * 发送消息的求职用户ID。
     */
    private Long userId;

    /**
     * 岗位ID。
     */
    private Long positionId;

    /**
     * 公司ID。
     */
    private Long companyId;

    /**
     * 消息发送方类型。
     * P表示参数描述，当前只有求职用户发给 HR，所以固定保存 USER，后续 HR 回复时可以扩展为 HR。
     */
    private String senderType;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 消息状态。
     * P表示参数描述，当前点击立即沟通后直接记为 SENT，后续做已读/回复可以继续扩展。
     */
    private String status;
}
