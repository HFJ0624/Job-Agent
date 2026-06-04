package com.job.common.entity.base;

import lombok.Getter;

/**
 * 作者:hfj
 * 功能:统一返回状态码枚举，约定后端返回给前端的业务码和提示语
 * 日期:2026/6/2 10:45
 */
@Getter
public enum ResultCodeEnum {

    SUCCESS(200, "操作成功"),

    LOGIN_ERROR(201, "用户名或密码错误"),

    VALIDATE_CODE_ERROR(202, "验证码错误"),

    DATA_ERROR(204, "数据异常"),

    LOGIN_AUTH(208, "请先登录"),

    USER_NAME_IS_EXISTS(209, "用户名已经存在"),

    ACCOUNT_STOP(216, "账号已停用"),

    NODE_ERROR(217, "该节点下有子节点，不可以删除"),

    STOCK_LESS(219, "库存不足"),

    VENUE_NAME_EXIST(220, "场馆名称已经存在"),

    PHONE_ERROR(221, "手机号错误或不存在"),

    PASSWORD_NOT_EQ(222, "新密码和旧密码不一致"),

    EMAIL_NOT_EXIST(223, "邮箱不存在"),

    BALANCE_NOT_ENOUGH(224, "用户余额不足，请充值"),

    TIME_ERROR(225, "开始时间不能晚于结束时间"),

    TIME_LESS(226, "预约时长不能小于1小时"),

    ILLEGAL_REQUEST(227, "非法请求"),

    LOGIN_PROHIBIT(301, "用户为禁用状态"),

    PARAM_ERROR(400, "请求参数错误"),

    BUSINESS_ERROR(500, "业务处理失败"),

    SYSTEM_ERROR(9999, "系统异常，请稍后重试");

    /**
     * 业务状态码。这里不是 HTTP 状态码，而是前后端约定的业务码。
     */
    private final Integer code;

    /**
     * 返回给前端展示的提示信息。
     */
    private final String message;

    /**
     * 构造统一状态码。
     *
     * @param code 业务状态码
     * @param message 返回给前端的提示信息
     */
    ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
