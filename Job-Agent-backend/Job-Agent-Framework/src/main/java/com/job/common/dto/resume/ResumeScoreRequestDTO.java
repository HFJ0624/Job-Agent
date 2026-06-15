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
     * 求职方向。
     * 说明: V2 不是 JD 匹配评分，这个字段只用于让评分建议更贴近用户想投递的方向。
     */
    @Size(max = 128, message = "求职方向长度不能超过128位")
    private String targetPosition;
}
