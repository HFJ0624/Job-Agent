package com.job.common.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台首页待处理事项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardPendingItemVO {

    private String title;

    private String content;

    private String level;
}
