package com.job.exception;

import com.job.common.entity.base.ResultCodeEnum;
import lombok.Getter;

/**
 * 作者:hfj
 * 功能:业务异常类，用于抛出用户不存在、账号禁用、手机号重复等业务错误
 * 日期:2026/6/2 10:45
 */
@Getter
public class BizException extends RuntimeException {

    /**
     * 业务状态码。
     */
    private final Integer code;

    /**
     * 根据错误信息创建业务异常。
     *
     * @param message 错误提示信息
     */
    public BizException(String message) {
        super(message);
        this.code = ResultCodeEnum.BUSINESS_ERROR.getCode();
    }

    /**
     * 根据统一状态码枚举创建业务异常。
     *
     * @param resultCodeEnum 统一状态码枚举
     */
    public BizException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum.getMessage());
        this.code = resultCodeEnum.getCode();
    }

    /**
     * 根据通用错误码创建业务异常。
     *
     * @param errorCode 通用错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 根据自定义状态码和提示创建业务异常。
     *
     * @param code 业务状态码
     * @param message 错误提示信息
     */
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 根据自定义状态码、提示和原始异常创建业务异常。
     *
     * @param code 业务状态码
     * @param message 错误提示信息
     * @param cause 原始异常对象
     */
    public BizException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
