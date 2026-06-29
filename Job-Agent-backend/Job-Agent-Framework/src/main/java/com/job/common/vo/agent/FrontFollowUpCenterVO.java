package com.job.common.vo.agent;

import com.job.common.vo.application.JobApplicationStatsVO;
import com.job.common.vo.reminder.ReminderStatsVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户端求职跟进 Agent 中心聚合数据。
 */
@Data
public class FrontFollowUpCenterVO {

    private JobApplicationStatsVO applicationStats;

    private ReminderStatsVO reminderStats;

    private List<FrontFollowUpApplicationVO> applications = new ArrayList<>();
}
