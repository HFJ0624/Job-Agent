package com.job.common.entity.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:用户实体类，对应数据库表 job_user
 * 日期:2026/6/2 10:45
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_user")
public class JobUser extends BaseEntity {

    /**
     * 用户名，注册和登录时可作为账号。
     */
    private String username;

    /**
     * BCrypt 加密后的密码，接口返回时必须过滤掉。
     */
    private String password;

    /**
     * 用户昵称，用于页面展示。
     */
    private String nickname;

    /**
     * 真实姓名，用于个人资料完善。
     */
    private String realName;


    /**
     * 手机号，登录时也可作为账号。
     */
    private String phone;

    /**
     * 邮箱，登录时也可作为账号。
     */
    private String email;

    /**
     * 头像地址。
     */
    private String avatarUrl;

    /**
     * 性别：0未知，1男，2女。
     */
    private Integer gender;

    /**
     * 最高学历。
     */
    private String education;

    /**
     * 工作年限，允许 1.5 这种小数。
     */
    private BigDecimal workYears;

    /**
     * 状态：0禁用，1正常。
     */
    private Integer status;
}
