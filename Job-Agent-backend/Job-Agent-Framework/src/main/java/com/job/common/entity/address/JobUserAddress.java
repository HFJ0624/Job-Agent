package com.job.common.entity.address;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:用户地址实体类，对应数据库 user_address 表
 * 日期:2026/6/4 11:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_address")
public class JobUserAddress extends BaseEntity {

    /**
     * 用户 ID，表示这条地址属于哪个用户。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 地址名称，例如 家、学校、公司。
     */
    @TableField("address_name")
    private String addressName;

    /**
     * 省份。
     */
    @TableField("province")
    private String province;

    /**
     * 城市。
     */
    @TableField("city")
    private String city;

    /**
     * 区县。
     */
    @TableField("district")
    private String district;

    /**
     * 详细地址，例如街道、门牌号、小区楼栋。
     */
    @TableField("detail_address")
    private String detailAddress;

    /**
     * 经度，高德地图选择地址时会带回来。
     */
    @TableField("longitude")
    private BigDecimal longitude;

    /**
     * 纬度，高德地图选择地址时会带回来。
     */
    @TableField("latitude")
    private BigDecimal latitude;

    /**
     * 是否默认地址：0 不是默认，1 是默认。
     */
    @TableField("is_default")
    private Integer isDefault;
}
