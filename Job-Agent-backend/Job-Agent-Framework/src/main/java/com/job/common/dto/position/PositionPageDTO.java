package com.job.common.dto.position;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:岗位分页查询请求参数，后台和前台岗位列表都可以复用
 * 日期:2026/6/6 15:20
 */
@Data
public class PositionPageDTO {

    /**
     * 当前页码，默认第 1 页。
     */
    @Min(value = 1, message = "pageNo不能小于1")
    private Long pageNo = 1L;

    /**
     * 每页条数，最大限制 100，避免一次拉取过多数据。
     */
    @Min(value = 1, message = "pageSize不能小于1")
    @Max(value = 100, message = "pageSize不能大于100")
    private Long pageSize = 10L;

    /**
     * 搜索关键词，可以按岗位名称、公司名称、城市、技能关键词搜索。
     */
    @Size(max = 64, message = "搜索关键词长度不能超过64位")
    private String keyword;

    /**
     * 公司ID，后台按公司筛选岗位时使用。
     */
    private Long companyId;

    /**
     * 工作城市。
     */
    @Size(max = 64, message = "城市长度不能超过64位")
    private String city;

    /**
     * 工作区县，比如浦东新区、西湖区。
     */
    @Size(max = 64, message = "区县长度不能超过64位")
    private String district;

    /**
     * 岗位类别。
     */
    @Size(max = 128, message = "岗位类别长度不能超过128位")
    private String jobCategory;

    /**
     * 学历要求，比如不限、大专、本科、硕士。
     */
    @Size(max = 64, message = "学历要求长度不能超过64位")
    private String educationReq;

    /**
     * 经验要求，比如不限、1-3年、3-5年。
     */
    @Size(max = 64, message = "经验要求长度不能超过64位")
    private String experienceReq;

    /**
     * 工作类型，比如全职、实习、远程。
     */
    @Size(max = 64, message = "工作类型长度不能超过64位")
    private String workType;

    /**
     * 岗位状态：0 草稿/下线，1 已发布；为空时后台查全部，前台会强制只查已发布。
     */
    private Integer status;
}
