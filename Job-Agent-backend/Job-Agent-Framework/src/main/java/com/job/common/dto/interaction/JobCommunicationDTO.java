package com.job.common.dto.interaction;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:立即沟通请求参数，用来接收求职用户发给 HR 的消息内容
 * 日期:2026/6/6 16:10
 */
@Data
public class JobCommunicationDTO {

    /**
     * 沟通消息内容。
     * P表示参数描述，允许为空；为空时后端会生成一条默认招呼语，方便用户一键发起沟通。
     */
    @Size(max = 500, message = "沟通内容长度不能超过500位")
    private String content;
}
