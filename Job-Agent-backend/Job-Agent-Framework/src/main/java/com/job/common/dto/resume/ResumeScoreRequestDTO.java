package com.job.common.dto.resume;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:简历评分请求参数
 * 日期:2026/6/6
 */
@Data
public class ResumeScoreRequestDTO {

    /**
     * 目标岗位。
     * 说明：第一版可以不传；如果传了，可以让评分建议更贴近目标方向。
     */
    @Size(max = 128, message = "目标岗位长度不能超过128位")
    private String targetPosition;
}
