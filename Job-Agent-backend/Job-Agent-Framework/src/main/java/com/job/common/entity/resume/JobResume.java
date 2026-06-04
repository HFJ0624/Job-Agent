package com.job.common.entity.resume;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:简历实体类，对应数据库 resume 表，记录用户上传到 MinIO 的简历信息
 * 日期:2026/6/4 10:30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume")
public class JobResume extends BaseEntity {

    /**
     * 用户 ID，用来标记这份简历属于哪个求职用户。
     */
    private Long userId;

    /**
     * 简历名称，前端展示时使用；同一个用户下不能重复。
     */
    private String resumeName;

    /**
     * 文件访问地址，保存 MinIO 返回的完整访问 URL。
     */
    private String fileUrl;

    /**
     * 原始文件名，例如 张三-Java后端.pdf。
     */
    private String fileName;

    /**
     * 文件类型，例如 PDF、DOC、DOCX。
     */
    private String fileType;

    /**
     * 文件大小，单位是字节。
     */
    private Long fileSize;

    /**
     * 原始解析文本，后续简历解析完成后写入。
     */
    private String rawText;

    /**
     * 结构化简历 JSON，后续 AI 解析完成后写入。
     */
    private String parsedJson;

    /**
     * 简历评分，后续 AI 评分完成后写入。
     */
    private BigDecimal score;

    /**
     * 简历状态：UPLOADED、PARSING、PARSED、PARSE_FAILED。
     */
    private String status;

    /**
     * 是否默认简历：0 不是默认，1 是默认。
     */
    private Integer isDefault;
}
