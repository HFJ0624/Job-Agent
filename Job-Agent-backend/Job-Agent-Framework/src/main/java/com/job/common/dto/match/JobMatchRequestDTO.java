package com.job.common.dto.match;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
/**
 * 作者:hfj
 * 功能:岗位匹配请求参数
 * 日期: 2026/6/8 10:57
 */
@Data
public class JobMatchRequestDTO {

    /**
     * 简历ID。
     * 说明：用户在岗位详情页选择哪份简历，就用哪份简历进行匹配。
     */
    @NotNull(message = "简历ID不能为空")
    private Long resumeId;
}
