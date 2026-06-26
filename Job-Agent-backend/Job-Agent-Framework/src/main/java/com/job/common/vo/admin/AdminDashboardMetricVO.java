package com.job.common.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台首页核心指标卡片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardMetricVO {

    private String label;

    private Long value;

    private String subText;
}
