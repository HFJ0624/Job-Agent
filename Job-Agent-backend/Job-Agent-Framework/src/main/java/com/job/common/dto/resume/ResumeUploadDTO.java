package com.job.common.dto.resume;

import com.job.common.entity.resume.JobResume;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:上传简历请求参数，接收前端传来的简历名称
 * 日期:2026/6/4 10:30
 */
@Data
public class ResumeUploadDTO {

    /**
     * 简历名称。
     * P表示参数描述：同一个用户可以上传多份简历，但是简历名称不能重复。
     */
    @NotBlank(message = "简历名称不能为空")
    @Size(max = 128, message = "简历名称长度不能超过128位")
    private String resumeName;

    /**
     * 将请求参数转换成简历实体。
     *
     * @param userId 当前登录用户 ID
     * @return 返回可交给 Service 继续补充文件信息的简历实体
     */
    public JobResume toEntity(Long userId) {
        // 1. DTO 只负责接收前端参数，文件地址、状态、时间等系统字段在 Service 中补齐。
        JobResume resume = new JobResume();
        resume.setUserId(userId);
        resume.setResumeName(resumeName);
        return resume;
    }
}
