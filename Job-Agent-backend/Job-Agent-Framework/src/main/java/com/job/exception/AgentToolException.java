package com.job.exception;

import com.job.common.entity.base.ResultCodeEnum;
import com.job.enums.AgentToolErrorCode;
import lombok.Getter;

/**
 * 作者:hfj
 * 功能:Agent 工具统一异常
 * 日期:2026/6/20
 */
@Getter
public class AgentToolException extends BizException {

    /**
     * 工具错误码。
     * 这个错误码比 ResultCodeEnum 更细，用于后台 Trace 和后续工具编排判断。
     */
    private final AgentToolErrorCode toolErrorCode;

    /**
     * 工具名称。
     */
    private final String toolName;

    /**
     * 创建工具异常。
     *
     * @param toolErrorCode 工具错误码
     * @param toolName 工具名称
     * @param message 错误信息
     */
    public AgentToolException(AgentToolErrorCode toolErrorCode, String toolName, String message) {
        super(ResultCodeEnum.BUSINESS_ERROR.getCode(), message);
        this.toolErrorCode = toolErrorCode;
        this.toolName = toolName;
    }

    /**
     * 创建带原始异常的工具异常。
     *
     * @param toolErrorCode 工具错误码
     * @param toolName 工具名称
     * @param message 错误信息
     * @param cause 原始异常
     */
    public AgentToolException(
            AgentToolErrorCode toolErrorCode,
            String toolName,
            String message,
            Throwable cause
    ) {
        super(ResultCodeEnum.BUSINESS_ERROR.getCode(), message, cause);
        this.toolErrorCode = toolErrorCode;
        this.toolName = toolName;
    }
}
