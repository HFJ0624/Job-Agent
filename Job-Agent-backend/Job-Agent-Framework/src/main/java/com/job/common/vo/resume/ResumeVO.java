package com.job.common.vo.resume;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.job.common.entity.resume.JobResume;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:简历响应对象，返回给前端展示已上传的简历信息
 * 日期:2026/6/4 10:30
 */
@Data
public class ResumeVO {

    /**
     * 简历 ID。
     * P表示参数描述，雪花 ID 很长，返回给前端时转成字符串，避免 JavaScript number 精度丢失。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户 ID。
     * P表示参数描述，同样转成字符串，避免前端接收大整数时发生精度问题。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 简历名称。
     */
    private String resumeName;

    /**
     * 文件访问地址，前端可以用它打开或下载简历。
     */
    private String fileUrl;

    /**
     * 原始文件名。
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
     * 原始解析文本。
     */
    private String rawText;

    /**
     * 结构化简历 JSON。
     */
    private String parsedJson;

    /**
     * 简历评分。
     */
    private BigDecimal score;

    /**
     * 简历状态。
     */
    private String status;

    /**
     * 是否默认简历：0 不是默认，1 是默认。
     */
    private Integer isDefault;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 将简历实体转换成前端响应对象。
     *
     * @param resume 数据库简历实体
     * @return 返回前端需要展示的简历信息
     */
    public static ResumeVO from(JobResume resume) {
        // 1. VO 只做展示字段转换，不在这里写任何数据库或 MinIO 逻辑。
        ResumeVO response = new ResumeVO();
        response.setId(resume.getId());
        response.setUserId(resume.getUserId());
        response.setResumeName(resume.getResumeName());
        response.setFileUrl(resume.getFileUrl());
        response.setFileName(resume.getFileName());
        response.setFileType(resume.getFileType());
        response.setFileSize(resume.getFileSize());
        response.setRawText(resume.getRawText());
        response.setParsedJson(resume.getParsedJson());
        response.setScore(resume.getScore());
        response.setStatus(resume.getStatus());
        response.setIsDefault(resume.getIsDefault());
        response.setCreateTime(resume.getCreateTime());
        response.setUpdateTime(resume.getUpdateTime());
        return response;
    }
}
