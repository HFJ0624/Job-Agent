package com.job.common.dto.communication;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: 标记用户已发送回复 DTO
 *
 * 使用场景:
 * 用户复制 AI 生成的话术并发送给 HR 后，
 * 点击“已发送给 HR”，系统保存最终发送内容。
 */
@Data
public class UserReplySentDTO {

    /**
     * 用户最终发送给 HR 的回复内容。
     *
     * 如果为空，后端可以默认使用 aiReplyText。
     */
    private String userReplyText;
}
