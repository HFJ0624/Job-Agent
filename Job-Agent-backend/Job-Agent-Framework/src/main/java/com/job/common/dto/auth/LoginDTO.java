package com.job.common.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:用户登录请求参数，接收前端登录表单
 * 日期:2026/6/2 10:45
 */
@Data
public class LoginDTO {

    /**
     * 登录账号，可以填写用户名、手机号或邮箱。
     */
    @NotBlank(message = "账号不能为空")
    private String account;

    /**
     * 登录密码，前端提交明文，后端用 BCrypt 进行校验。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在6到32位之间")
    private String password;
}
