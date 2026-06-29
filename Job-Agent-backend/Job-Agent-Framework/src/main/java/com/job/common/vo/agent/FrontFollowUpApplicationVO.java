package com.job.common.vo.agent;

import com.job.common.vo.application.JobApplicationVO;
import com.job.common.vo.reminder.JobReminderVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户端求职跟进 Agent 单个岗位聚合视图。
 */
@Data
public class FrontFollowUpApplicationVO {

    /**
     * 求职投递记录。
     */
    private JobApplicationVO application;

    /**
     * 该投递下待处理提醒。
     */
    private List<JobReminderVO> pendingReminders = new ArrayList<>();

    /**
     * Agent 生成的下一步建议动作。
     */
    private List<FrontFollowUpActionVO> suggestedActions = new ArrayList<>();

    /**
     * 当前卡片优先级：HIGH / NORMAL / LOW。
     */
    private String priority = "NORMAL";

    /**
     * 优先级说明。
     */
    private String priorityReason;
}
