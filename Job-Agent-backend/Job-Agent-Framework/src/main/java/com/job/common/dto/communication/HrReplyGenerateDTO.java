package com.job.common.dto.communication;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: 保存 HR 回复并生成 AI 建议回复 DTO
 *
 * 使用场景:
 * 用户把 Boss 直聘等平台 HR 回复复制进系统，
 * 系统根据岗位、简历、HR 回复内容生成一段建议回复。
 */
@Data
public class HrReplyGenerateDTO {

    /**
     * HR 回复内容。
     */
    private String hrReply;

    /**
     * 用户手动选择的当前求职进展状态。
     *
     * 例如:
     * REPLIED
     * INTERVIEW_INVITED
     * NO_REPLY
     * CLOSED
     *
     * 如果前端不传，后端默认使用 REPLIED。
     */
    private String progressStatus;

    /**
     * 回复风格。
     *
     * 例如:
     * 自然、礼貌、积极、简洁、正式。
     */
    private String replyStyle;

    /**
     * 用户额外要求。
     *
     * 例如:
     * “帮我委婉表达明天下午不方便”
     * “回复得积极一点”
     * “帮我问清楚面试形式”
     */
    private String userRequirement;

    /**
     * 备注。
     */
    private String note;
}
