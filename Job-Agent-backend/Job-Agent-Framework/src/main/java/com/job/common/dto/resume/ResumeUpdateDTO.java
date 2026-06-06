package com.job.common.dto.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:修改简历名称请求参数
 * 日期:2026/6/4 16:40
 */
@Data
public class ResumeUpdateDTO {

    /**
     * 新的简历名称。
     * P表示参数描述：同一个用户下简历名称不能重复，后端会再次校验，避免只依赖前端判断。
     */
    @NotBlank(message = "简历名称不能为空")
    @Size(max = 128, message = "简历名称长度不能超过128位")
    private String resumeName;
}
