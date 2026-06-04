package com.job.common.vo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.user.JobUser;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:用户信息响应对象，返回给前端展示用户资料
 * 日期:2026/6/2 10:45
 */
@Data
public class UserVO {

    /**
     * 用户 ID。
     */
    private Long id;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 昵称。
     */
    private String nickname;

    /**
     * 真实姓名。
     */
    private String realName;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 邮箱。
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
     * 学历。
     */
    private String education;

    /**
     * 工作年限。
     */
    private BigDecimal workYears;

    /**
     * 账号状态：0禁用，1正常。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 将用户实体转换成前端响应对象。
     *
     * @param user 数据库用户实体
     * @return 返回给前端的用户信息，不包含 password
     */
    public static UserVO from(JobUser user) {
        // 1. 响应对象不提供 password 字段，避免密码哈希泄露到前端。
        UserVO response = new UserVO();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRealName(user.getRealName());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setGender(user.getGender());
        response.setEducation(user.getEducation());
        response.setWorkYears(user.getWorkYears());
        response.setStatus(user.getStatus());
        response.setCreateTime(user.getCreateTime());
        response.setUpdateTime(user.getUpdateTime());
        return response;
    }
}
