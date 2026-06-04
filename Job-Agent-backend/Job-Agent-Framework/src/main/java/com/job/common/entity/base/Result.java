package com.job.common.entity.base;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:统一接口返回结果，前端所有接口都按这个结构接收
 * 日期:2026/6/2 10:45
 */
@Data
@Schema(description = "统一响应结果")
public class Result<T> {

    /**
     * 业务状态码，200 表示成功。
     */
    @Schema(description = "业务状态码")
    private Integer code;

    /**
     * 返回给前端展示的提示信息。
     */
    @Schema(description = "响应消息")
    private String message;

    /**
     * 真实业务数据，比如用户信息、分页列表等。
     */
    @Schema(description = "业务数据")
    private T data;

    private Result() {
    }

    /**
     * 按指定 code 和 message 构建响应。
     *
     * @param body 业务数据
     * @param code 业务状态码
     * @param message 响应提示信息
     * @return 返回统一响应对象
     */
    public static <T> Result<T> build(T body, Integer code, String message) {
        // 1. 使用私有构造方法创建对象，避免外部随意 new 出半成品结果。
        Result<T> result = new Result<>();
        result.setData(body);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 按统一状态码枚举构建响应。
     *
     * @param body 业务数据
     * @param resultCodeEnum 状态码枚举
     * @return 返回统一响应对象
     */
    public static <T> Result<T> build(T body, ResultCodeEnum resultCodeEnum) {
        return build(body, resultCodeEnum.getCode(), resultCodeEnum.getMessage());
    }
}
