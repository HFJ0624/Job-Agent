package com.job.common.dto.communication;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: 保存 HR 回复 DTO
 */
@Data
public class JobCommunicationReplyDTO {

    /**
     * HR 回复内容。
     */
    private String hrReply;

    /**
     * 备注。
     */
    private String note;
}
