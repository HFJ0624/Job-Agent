package com.job.common.entity.base;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:基础实体类，封装数据库表通用字段
 * 日期:2026/6/2 10:45
 */
@Data
public class BaseEntity implements Serializable {

    /**
     * 主键 ID。
     */
    @Schema(description = "唯一标识")
    private Long id;

    /**
     * 数据创建时间。
     */
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 数据最后更新时间。
     */
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 逻辑删除标记，0 表示未删除，1 表示已删除。
     */
    @Schema(description = "是否删除")
    private Integer isDeleted;
}
