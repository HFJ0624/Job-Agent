package com.job.common.dto.company;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:公司分页查询请求参数，后台公司列表用它接收筛选条件
 * 日期:2026/6/6 10:30
 */
@Data
public class CompanyPageDTO {

    /**
     * 当前页码，默认第 1 页。
     */
    @Min(value = 1, message = "pageNo不能小于1")
    private Long pageNo = 1L;

    /**
     * 每页条数，最大限制 100，防止一次请求把列表拉得太大。
     */
    @Min(value = 1, message = "pageSize不能小于1")
    @Max(value = 100, message = "pageSize不能大于100")
    private Long pageSize = 10L;

    /**
     * 搜索关键词，可以按公司名称、行业、城市、地址模糊查询。
     */
    @Size(max = 64, message = "搜索关键词长度不能超过64位")
    private String keyword;

    /**
     * 公司状态：0 禁用，1 正常；为空时查询全部状态。
     */
    private Integer status;
}
