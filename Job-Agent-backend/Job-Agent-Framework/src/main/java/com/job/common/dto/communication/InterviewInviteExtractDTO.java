package com.job.common.dto.communication;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: 面试邀约信息提取请求 DTO
 * 使用场景:
 * 用户可以从已有 HR 回复中提取，也可以手动传一段 HR 回复文本提取。
 */
@Data
public class InterviewInviteExtractDTO {

    /**
     * HR 回复内容。
     *
     * 如果为空，后端默认使用沟通记录里的 hrReply。
     */
    private String hrReply;
}
