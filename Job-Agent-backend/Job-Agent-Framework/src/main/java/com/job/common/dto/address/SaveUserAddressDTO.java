package com.job.common.dto.address;

import com.job.common.entity.address.JobUserAddress;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:保存用户家庭地址请求参数，接收手动填写或高德地图选择的地址信息
 * 日期:2026/6/4 11:00
 */
@Data
public class SaveUserAddressDTO {

    /**
     * 地址 ID。
     * P表示参数描述，新增地址时可以为空，修改已有地址时前端带回来。
     */
    private Long id;

    /**
     * 地址名称，例如 家、学校、公司。
     */
    @Size(max = 64, message = "地址名称长度不能超过64位")
    private String addressName;

    /**
     * 省份。
     */
    @Size(max = 64, message = "省份长度不能超过64位")
    private String province;

    /**
     * 城市。
     */
    @Size(max = 64, message = "城市长度不能超过64位")
    private String city;

    /**
     * 区县。
     */
    @Size(max = 64, message = "区县长度不能超过64位")
    private String district;

    /**
     * 详细地址。
     */
    @Size(max = 255, message = "详细地址长度不能超过255位")
    private String detailAddress;

    /**
     * 经度，来自高德地图时会自动填充；手动填写时可以为空。
     */
    @DecimalMin(value = "-180", message = "经度不能小于-180")
    @DecimalMax(value = "180", message = "经度不能大于180")
    private BigDecimal longitude;

    /**
     * 纬度，来自高德地图时会自动填充；手动填写时可以为空。
     */
    @DecimalMin(value = "-90", message = "纬度不能小于-90")
    @DecimalMax(value = "90", message = "纬度不能大于90")
    private BigDecimal latitude;

    /**
     * 将请求参数转换成用户地址实体。
     *
     * @param userId 当前登录用户 ID
     * @return 返回可交给 Service 保存的地址实体
     */
    public JobUserAddress toEntity(Long userId) {
        // 1. DTO 只做参数承接，默认值、归属校验和时间字段在 Service 中处理。
        JobUserAddress address = new JobUserAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setAddressName(addressName);
        address.setProvince(province);
        address.setCity(city);
        address.setDistrict(district);
        address.setDetailAddress(detailAddress);
        address.setLongitude(longitude);
        address.setLatitude(latitude);
        return address;
    }
}
