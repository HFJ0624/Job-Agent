package com.job.exception;

import lombok.Getter;

/**
 * 作者:hfj
 * 功能:通用错误码枚举，给业务异常提供更清楚的错误分类
 * 日期:2026/6/2 10:45
 */
@Getter
public enum ErrorCode {

    PARAM_ERROR(400, "请求参数错误"),
    NOT_LOGIN(208, "请先登录"),
    BUSINESS_ERROR(500, "业务处理失败"),
    SYSTEM_ERROR(9999, "系统异常，请稍后重试");

    private final Integer code;

    /**
     * 错误提示信息。
     */
    private final String message;

    /**
     * 构造通用错误码。
     *
     * @param code 业务状态码
     * @param message 错误提示信息
     */
    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
