package com.job.common.entity.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 作者:hfj
 * 功能:通用分页返回对象，封装列表数据和分页信息
 * 日期:2026/6/2 10:45
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    /**
     * 当前页的数据列表。
     */
    private List<T> records;

    /**
     * 符合条件的总条数。
     */
    private Long total;

    /**
     * 当前页码。
     */
    private Long pageNo;

    /**
     * 每页条数。
     */
    private Long pageSize;

    /**
     * 构造一个空分页结果。
     *
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @return 返回空列表分页对象
     */
    public static <T> PageResult<T> empty(Long pageNo, Long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNo, pageSize);
    }
}
