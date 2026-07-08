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
 * 求职提醒服务接口。
 *
 * <p>核心职责：基于求职进度和沟通记录，自动或手动创建提醒事项，支持到期通知、延期、完成和取消。</p>
 *
 * <p>所属业务模块：求职管理 - 提醒通知</p>
 *
 * <p>主要调用链：
 * JobReminderController / JobApplicationService / JobCommunicationRecordService -&gt; JobReminderService -&gt; JobReminderServiceImpl -&gt; JobReminderRepository / Scheduler</p>
 */
public interface JobReminderService {

    /**
     * 分页查询提醒列表。
     *
     * @param userId 当前用户 ID
     * @param queryDTO 查询条件
     * @return 提醒分页结果
     */
    ReminderPageVO pageReminders(Long userId, ReminderQueryDTO queryDTO);

    /**
     * 查询提醒统计。
     *
     * @param userId 当前用户 ID
     * @return 提醒统计数据
     */
    ReminderStatsVO getStats(Long userId);

    /**
     * 查询当前已到期提醒。
     *
     * @param userId 当前用户 ID
     * @return 已到期提醒列表
     */
    List<JobReminderVO> listDueReminders(Long userId);

    /**
     * 创建自定义提醒。
     *
     * @param userId 当前用户 ID
     * @param dto 提醒创建参数
     * @return 创建后的提醒
     */
    JobReminderVO createReminder(Long userId, ReminderCreateDTO dto);

    /**
     * 根据沟通记录同步提醒。
     *
     * <p>场景：</p>
     * <ol>
     *   <li>用户确认面试邀约后，自动创建面试提醒。</li>
     *   <li>用户设置 nextFollowTime 后，自动创建跟进提醒。</li>
     * </ol>
     *
     * @param userId 当前用户 ID
     * @param record 沟通记录实体
     */
    void syncFromCommunicationRecord(Long userId, JobCommunicationRecord record);

    /**
     * 标记提醒为已完成。
     *
     * @param userId 当前用户 ID
     * @param reminderId 提醒 ID
     * @return 更新后的提醒
     */
    JobReminderVO markDone(Long userId, Long reminderId);

    /**
     * 标记提醒为已读。
     *
     * @param userId 当前用户 ID
     * @param reminderId 提醒 ID
     * @return 更新后的提醒
     */
    JobReminderVO markRead(Long userId, Long reminderId);

    /**
     * 取消提醒。
     *
     * @param userId 当前用户 ID
     * @param reminderId 提醒 ID
     * @param reason 取消原因
     * @return 更新后的提醒
     */
    JobReminderVO cancelReminder(Long userId, Long reminderId, String reason);

    /**
     * 延期提醒。
     *
     * @param userId 当前用户 ID
     * @param reminderId 提醒 ID
     * @param dto 延期参数
     * @return 更新后的提醒
     */
    JobReminderVO postponeReminder(Long userId, Long reminderId, ReminderPostponeDTO dto);
}
