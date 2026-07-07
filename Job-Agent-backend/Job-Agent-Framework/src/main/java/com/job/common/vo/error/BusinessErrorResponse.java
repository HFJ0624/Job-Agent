package com.job.common.vo.error;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 业务异常扩展响应体。
 *
 * 说明:
 * 1. Result.code/message 继续保持原有统一响应结构。
 * 2. errorCode 提供稳定的细粒度错误码，前端可以据此判断 ASR、模型、RAG 等具体失败步骤。
 * 3. message 冗余保存用户可读提示，方便前端在只读取 data 时也能拿到完整错误信息。
 */
@Data
@AllArgsConstructor
public class BusinessErrorResponse {

    /**
     * 稳定业务错误码，例如 MOCK_INTERVIEW_ASR_FAILED。
     */
    private String errorCode;

    /**
     * 用户可读错误提示。
     */
    private String message;
}
