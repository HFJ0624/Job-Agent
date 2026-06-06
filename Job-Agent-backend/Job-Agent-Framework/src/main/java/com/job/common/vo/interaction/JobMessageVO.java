package com.job.common.vo.interaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.interaction.JobPositionMessage;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:岗位沟通消息响应对象，返回给前端展示“消息已发送给 HR”
 * 日期:2026/6/6 16:10
 */
@Data
public class JobMessageVO {

    /**
     * 消息ID。
     */
    private Long id;

    /**
     * 岗位ID。
     */
    private Long positionId;

    /**
     * 公司ID。
     */
    private Long companyId;

    /**
     * 接收人名称，当前用“公司名称 HR”展示。
     */
    private String receiverName;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 消息状态。
     */
    private String status;

    /**
     * 发送时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 将消息实体转换成前端响应对象。
     *
     * @param message 消息实体
     * @param companyName 公司名称
     * @return 返回消息响应对象
     */
    public static JobMessageVO from(JobPositionMessage message, String companyName) {
        JobMessageVO response = new JobMessageVO();
        response.setId(message.getId());
        response.setPositionId(message.getPositionId());
        response.setCompanyId(message.getCompanyId());
        response.setReceiverName((companyName == null || companyName.isBlank()) ? "招聘 HR" : companyName + " HR");
        response.setContent(message.getContent());
        response.setStatus(message.getStatus());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}
