package com.job.common.dto.user;

import com.job.common.entity.user.JobUser;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:修改当前登录用户资料的请求参数
 * 日期:2026/6/2 10:45
 */
@Data
public class UpdateUserProfileRequest {

    /**
     * 昵称。
     */
    @Size(max = 64, message = "昵称长度不能超过64位")
    private String nickname;

    /**
     * 真实姓名。
     */
    @Size(max = 64, message = "真实姓名长度不能超过64位")
    private String realName;

    /**
     * 手机号，可以为空，有值时必须符合手机号格式。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 邮箱，可以为空，有值时必须符合邮箱格式。
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128位")
    private String email;

    /**
     * 头像地址。
     */
    @Size(max = 512, message = "头像地址长度不能超过512位")
    private String avatarUrl;

    /**
     * 性别：0未知，1男，2女。
     */
    private Integer gender;

    /**
     * 学历。
     */
    @Size(max = 64, message = "学历长度不能超过64位")
    private String education;

    /**
     * 工作年限，不能小于 0。
     */
    @DecimalMin(value = "0.0", message = "工作年限不能小于0")
    private BigDecimal workYears;

    /**
     * 将资料请求转换成用户实体。
     *
     * @return 返回只包含可修改资料字段的用户实体
     */
    public JobUser toEntity() {
        // 1. 故意不设置 username、password、status、isDeleted，防止前端越权修改。
        JobUser user = new JobUser();
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
