package com.job.common.vo.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作者:hfj
 * 功能:文件上传成功后的响应对象
 * 日期:2026/6/2 15:20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadVO {

    /**
     * 文件访问地址，前端保存到 avatarUrl 字段。
     */
    private String url;

    /**
     * 文件在 MinIO 中的对象名称。
     */
    private String objectName;

    /**
     * 文件原始名称。
     */
    private String originalFilename;
}
