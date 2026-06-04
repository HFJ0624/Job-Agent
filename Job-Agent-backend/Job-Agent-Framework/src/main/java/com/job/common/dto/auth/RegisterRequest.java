package com.job.common.dto.auth;

import com.job.common.entity.user.JobUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:用户注册请求参数，接收前端注册表单
 * 日期:2026/6/2 10:45
 */
@Data
public class RegisterRequest {

    /**
     * 用户名，注册后可以用于登录。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度必须在4到32位之间")
    private String username;

    /**
     * 密码，保存前会在 Service 中加密。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在6到32位之间")
    private String password;

    /**
     * 昵称，可以为空。
     */
    @Size(max = 64, message = "昵称长度不能超过64位")
    private String nickname;

    /**
     * 真实姓名，可以为空。
     */
    @Size(max = 64, message = "真实姓名长度不能超过64位")
    private String realName;

    /**
     * 手机号，可以为空，但有值时必须符合手机号格式。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 邮箱，可以为空，但有值时必须符合邮箱格式。
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128位")
    private String email;

    /**
     * 头像地址，由头像上传接口返回。
     */
    @Size(max = 512, message = "头像地址长度不能超过512位")
    private String avatarUrl;

    /**
     * 性别：0未知，1男，2女。
     */
    private Integer gender;

    /**
     * 教育经历。
     */
    @Size(max = 64, message = "教育经历长度不能超过64位")
    private String education;

    /**
     * 工作年限。
     */
    @DecimalMin(value = "0.0", message = "工作年限不能小于0")
    private BigDecimal workYears;

    /**
     * 将注册请求转换成用户实体。
     *
     * @return 返回可交给 Service 保存的用户实体
     */
    public JobUser toEntity() {
        // 1. DTO 只负责接收参数，真正的密码加密和默认值设置放在 Service 中。
        JobUser user = new JobUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(nickname);
        user.setRealName(realName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        user.setGender(gender);
        user.setEducation(education);
        user.setWorkYears(workYears);
        return user;
    }
}
