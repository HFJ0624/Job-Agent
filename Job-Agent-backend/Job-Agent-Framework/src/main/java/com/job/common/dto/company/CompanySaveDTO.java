package com.job.common.dto.company;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:公司新增和修改请求参数，后台表单提交时使用
 * 日期:2026/6/6 10:30
 */
@Data
public class CompanySaveDTO {

    /**
     * 公司名称，必填；新增和修改时都要校验，避免列表里出现空公司。
     */
    @NotBlank(message = "公司名称不能为空")
    @Size(max = 255, message = "公司名称长度不能超过255位")
    private String companyName;

    /**
     * 公司 Logo 地址，允许为空。
     */
    @Size(max = 512, message = "Logo地址长度不能超过512位")
    private String logoUrl;

    /**
     * 行业。
     */
    @Size(max = 128, message = "行业长度不能超过128位")
    private String industry;

    /**
     * 公司规模。
     */
    @Size(max = 64, message = "公司规模长度不能超过64位")
    private String companySize;

    /**
     * 融资阶段。
     */
    @Size(max = 64, message = "融资阶段长度不能超过64位")
    private String financingStage;

    /**
     * 公司简介。
     */
    private String description;

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
    @Size(max = 255, message = "公司地址长度不能超过255位")
    private String address;

    /**
     * 经度，最多保留 6 位小数。
     */
    @Digits(integer = 10, fraction = 6, message = "经度最多10位整数和6位小数")
    private BigDecimal longitude;

    /**
     * 纬度，最多保留 6 位小数。
     */
    @Digits(integer = 10, fraction = 6, message = "纬度最多10位整数和6位小数")
    private BigDecimal latitude;

    /**
     * 发展前景分数，建议范围 0-10。
     */
    @DecimalMin(value = "0", message = "发展前景分数不能小于0")
    @DecimalMax(value = "10", message = "发展前景分数不能大于10")
    @Digits(integer = 5, fraction = 2, message = "发展前景分数最多5位整数和2位小数")
    private BigDecimal prospectScore;

    /**
     * 状态：0 禁用，1 正常；为空时后端默认按正常处理。
     */
    private Integer status;
}
