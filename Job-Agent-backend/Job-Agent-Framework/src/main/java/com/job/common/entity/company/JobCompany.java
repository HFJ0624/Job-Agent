package com.job.common.entity.company;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:公司实体类，对应数据库 company 表，用来保存招聘公司基础资料
 * 日期:2026/6/6 10:30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("company")
public class JobCompany extends BaseEntity {

    /**
     * 公司名称，列表展示和 Excel 导入查重都会使用这个字段。
     */
    private String companyName;

    /**
     * 公司 Logo 地址，可以是外部图片 URL，也可以后续扩展为 MinIO 地址。
     */
    private String logoUrl;

    /**
     * 所属行业，例如互联网、人工智能、新能源汽车等。
     */
    private String industry;

    /**
     * 公司规模，例如 1000-9999人、10000人以上。
     */
    private String companySize;

    /**
     * 融资阶段，例如未融资、已上市、B轮等。
     */
    private String financingStage;

    /**
     * 公司简介，给前台职位推荐、AI 分析和后台查看使用。
     */
    private String description;

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
     * 公司详细地址。
     */
    private String address;

    /**
     * 公司经度，高德地图或 Excel 导入时写入。
     */
    private BigDecimal longitude;

    /**
     * 公司纬度，高德地图或 Excel 导入时写入。
     */
    private BigDecimal latitude;

    /**
     * 发展前景分数，支持一位或两位小数，用来给公司做简单排序参考。
     */
    private BigDecimal prospectScore;

    /**
     * 公司状态：0 禁用，1 正常。
     */
    private Integer status;
}
