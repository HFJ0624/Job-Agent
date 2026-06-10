package com.job.bootstrap.service;

import com.job.common.dto.reminder.ReminderCreateDTO;
import com.job.common.dto.reminder.ReminderPostponeDTO;
import com.job.common.dto.reminder.ReminderQueryDTO;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.common.vo.reminder.JobReminderVO;
import com.job.common.vo.reminder.ReminderPageVO;
import com.job.common.vo.reminder.ReminderStatsVO;

import java.util.List;

/**
 * 作者: hfj
 * 功能: 求职提醒服务
 */
public interface JobReminderService {

    /**
     * 分页查询提醒列表。
     */
    ReminderPageVO pageReminders(Long userId, ReminderQueryDTO queryDTO);

    /**
     * 查询提醒统计。
     */
    ReminderStatsVO getStats(Long userId);

    /**
     * 查询当前已到期提醒。
     */
    List<JobReminderVO> listDueReminders(Long userId);

    /**
     * 创建自定义提醒。
     */
    JobReminderVO createReminder(Long userId, ReminderCreateDTO dto);

    /**
     * 根据沟通记录同步提醒。
     *
     * 场景:
     * 1. 用户确认面试邀约后，自动创建面试提醒。
     * 2. 用户设置 nextFollowTime 后，自动创建跟进提醒。
     */
    void syncFromCommunicationRecord(Long userId, JobCommunicationRecord record);

    /**
     * 标记完成。
     */
    JobReminderVO markDone(Long userId, Long reminderId);

    /**
     * 标记已读。
     */
    JobReminderVO markRead(Long userId, Long reminderId);

    /**
     * 取消提醒。
     */
    JobReminderVO cancelReminder(Long userId, Long reminderId, String reason);

    /**
     * 延期提醒。
     */
    JobReminderVO postponeReminder(Long userId, Long reminderId, ReminderPostponeDTO dto);
}
