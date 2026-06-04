package com.job.common.dto.auth;

import com.job.common.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作者:hfj
 * 功能:登录成功响应对象，返回 token 和当前用户信息
 * 日期:2026/6/2 10:45
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * Token 名称，默认是 satoken。
     */
    private String tokenName;

    /**
     * Token 值，前端后续请求需要带上它。
     */
    private String tokenValue;

    /**
     * 当前登录用户信息，不包含 password。
     */
    private UserResponse user;
}
