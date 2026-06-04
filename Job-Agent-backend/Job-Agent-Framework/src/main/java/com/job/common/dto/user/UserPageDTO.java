package com.job.common.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:用户分页查询参数，接收管理后台用户列表筛选条件
 * 日期:2026/6/2 10:45
 */
@Data
public class UserPageDTO {

    /**
     * 当前页码，默认第 1 页。
     */
    @Min(value = 1, message = "pageNo不能小于1")
    private Long pageNo = 1L;

    /**
     * 每页条数，默认 10 条，最多 100 条。
     */
    @Min(value = 1, message = "pageSize不能小于1")
    @Max(value = 100, message = "pageSize不能大于100")
    private Long pageSize = 10L;

    /**
     * 搜索关键词，可以按用户名、昵称、手机号、邮箱模糊查询。
     */
    @Size(max = 64, message = "搜索关键词长度不能超过64位")
    private String keyword;
}
