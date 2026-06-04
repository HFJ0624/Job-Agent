package com.job.common.vo.address;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.job.common.entity.address.JobUserAddress;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:用户地址响应对象，返回给前端展示家庭地址
 * 日期:2026/6/4 11:00
 */
@Data
public class UserAddressVO {

    /**
     * 地址 ID。
     * P表示参数描述，雪花 ID 返回给前端时转成字符串，避免 JavaScript 精度丢失。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 地址名称。
     */
    private String addressName;

    /**
     * 省份。
     */
    private String province;

    /**
     * 城市。
     */
    private String city;

    /**
     * 区县。
     */
    private String district;

    /**
     * 详细地址。
     */
    private String detailAddress;

    /**
     * 经度。
     */
    private BigDecimal longitude;

    /**
     * 纬度。
     */
    private BigDecimal latitude;

    /**
     * 是否默认地址：0 不是默认，1 是默认。
     */
    private Integer isDefault;

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
     * 将地址实体转换成前端响应对象。
     *
     * @param address 数据库地址实体
     * @return 返回前端展示用的地址信息
     */
    public static UserAddressVO from(JobUserAddress address) {
        // 1. 允许用户还没有填写地址，所以这里对空值做兜底。
        if (address == null) {
            return null;
        }

        UserAddressVO response = new UserAddressVO();
        response.setId(address.getId());
        response.setUserId(address.getUserId());
        response.setAddressName(address.getAddressName());
        response.setProvince(address.getProvince());
        response.setCity(address.getCity());
        response.setDistrict(address.getDistrict());
        response.setDetailAddress(address.getDetailAddress());
        response.setLongitude(address.getLongitude());
        response.setLatitude(address.getLatitude());
        response.setIsDefault(address.getIsDefault());
        response.setCreateTime(address.getCreateTime());
        response.setUpdateTime(address.getUpdateTime());
        return response;
    }
}
