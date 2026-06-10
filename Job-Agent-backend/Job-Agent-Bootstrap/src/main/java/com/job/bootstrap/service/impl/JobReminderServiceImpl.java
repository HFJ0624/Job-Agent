package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.JobReminderMapper;
import com.job.bootstrap.service.JobReminderService;
import com.job.common.dto.reminder.ReminderCreateDTO;
import com.job.common.dto.reminder.ReminderPostponeDTO;
import com.job.common.dto.reminder.ReminderQueryDTO;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.common.entity.reminder.JobReminder;
import com.job.common.vo.reminder.JobReminderVO;
import com.job.common.vo.reminder.ReminderPageVO;
import com.job.common.vo.reminder.ReminderStatsVO;
import com.job.enums.ReminderStatus;
import com.job.enums.ReminderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 作者: hfj
 * 功能: 求职提醒服务实现
 *
 * 设计说明:
 * 1. 这层负责提醒任务的创建、更新、查询和状态流转。
 * 2. 沟通记录确认面试后，不直接在 Controller 里创建提醒，而是调用这里统一处理。
 * 3. 这样后续如果求职进度、Agent、定时任务都要创建提醒，可以复用同一套逻辑。
 */
@Service
@RequiredArgsConstructor
public class JobReminderServiceImpl implements JobReminderService {

    private static final int NOT_DELETED = 0;

    private static final int UNREAD = 0;

    private static final int READ = 1;

    /**
     * 默认面试提前 30 分钟提醒。
     */
    private static final int DEFAULT_INTERVIEW_ADVANCE_MINUTES = 30;

    private final JobReminderMapper jobReminderMapper;

    /**
     * 分页查询提醒。
     */
    @Override
    public ReminderPageVO pageReminders(Long userId, ReminderQueryDTO queryDTO) {
        Long pageNo = queryDTO.getPageNo() == null ? 1L : queryDTO.getPageNo();
        Long pageSize = queryDTO.getPageSize() == null ? 10L : queryDTO.getPageSize();

        Page<JobReminderVO> page = jobReminderMapper.selectReminderPage(
                new Page<>(pageNo, pageSize),
                userId,
                queryDTO
        );

        /*
         * 填充中文描述、是否过期、剩余分钟数等展示字段。
         */
        page.getRecords().forEach(this::fillDisplayFields);

        ReminderPageVO vo = new ReminderPageVO();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);

        return vo;
    }

    /**
     * 查询统计信息。
     */
    @Override
    public ReminderStatsVO getStats(Long userId) {
        ReminderStatsVO stats = new ReminderStatsVO();

        Date now = new Date();

        stats.setPendingCount(count(userId, null, ReminderStatus.PENDING.name(), null, null));
        stats.setDueCount(countDue(userId, now));
        stats.setTodayCount(countToday(userId));
        stats.setInterviewCount(count(userId, ReminderType.INTERVIEW.name(), ReminderStatus.PENDING.name(), null, null));
        stats.setFollowUpCount(count(userId, ReminderType.FOLLOW_UP.name(), ReminderStatus.PENDING.name(), null, null));
        stats.setUnreadCount(countUnread(userId));

        return stats;
    }

    /**
     * 查询当前已到期提醒。
     *
     * 使用场景:
     * 前端进入首页或求职进度页时调用，展示“你有几个待处理提醒”。
     */
    @Override
    public List<JobReminderVO> listDueReminders(Long userId) {
        ReminderQueryDTO queryDTO = new ReminderQueryDTO();
        queryDTO.setPageNo(1L);
        queryDTO.setPageSize(20L);
        queryDTO.setReminderStatus(ReminderStatus.PENDING.name());
        queryDTO.setEndTime(new Date());

        ReminderPageVO pageVO = pageReminders(userId, queryDTO);

        return pageVO.getRecords();
    }

    /**
     * 创建自定义提醒。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobReminderVO createReminder(Long userId, ReminderCreateDTO dto) {
        if (!StringUtils.hasText(dto.getReminderTitle())) {
            throw new IllegalArgumentException("提醒标题不能为空");
        }

        if (dto.getRemindTime() == null) {
            throw new IllegalArgumentException("提醒时间不能为空");
        }

        JobReminder reminder = new JobReminder();
        reminder.setUserId(userId);
        reminder.setApplicationId(dto.getApplicationId());
        reminder.setCommunicationId(dto.getCommunicationId());
        reminder.setResumeId(dto.getResumeId());
        reminder.setJobId(dto.getJobId());

        reminder.setReminderType(
                StringUtils.hasText(dto.getReminderType())
                        ? dto.getReminderType()
                        : ReminderType.CUSTOM.name()
        );

        reminder.setReminderTitle(dto.getReminderTitle());
        reminder.setReminderContent(dto.getReminderContent());
        reminder.setEventTime(dto.getEventTime());
        reminder.setRemindTime(dto.getRemindTime());
        reminder.setAdvanceMinutes(dto.getAdvanceMinutes() == null ? 0 : dto.getAdvanceMinutes());

        reminder.setReminderStatus(ReminderStatus.PENDING.name());
        reminder.setIsRead(UNREAD);
        reminder.setIsDeleted(NOT_DELETED);

        jobReminderMapper.insert(reminder);

        return getReminderVO(userId, reminder.getId());
    }

    /**
     * 根据沟通记录同步提醒。
     *
     * 这个方法是本模块核心。
     *
     * 情况一:
     * record.interviewTime 不为空，创建或更新面试提醒。
     *
     * 情况二:
     * record.nextFollowTime 不为空，创建或更新跟进提醒。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncFromCommunicationRecord(Long userId, JobCommunicationRecord record) {
        if (record == null) {
            return;
        }

        /*
         * 如果用户确认了面试时间，则创建或更新面试提醒。
         */
        if (record.getInterviewTime() != null) {
            createOrUpdateInterviewReminder(userId, record);
        }

        /*
         * 如果用户设置了下次跟进时间，则创建或更新跟进提醒。
         */
        if (record.getNextFollowTime() != null) {
            createOrUpdateFollowUpReminder(userId, record);
        }
    }

    /**
     * 标记提醒完成。
     */
    @Override
    public JobReminderVO markDone(Long userId, Long reminderId) {
        JobReminder reminder = getUserReminderRequired(userId, reminderId);

        reminder.setReminderStatus(ReminderStatus.DONE.name());
        reminder.setIsRead(READ);
        reminder.setDoneTime(new Date());

        jobReminderMapper.updateById(reminder);

        return getReminderVO(userId, reminderId);
    }

    /**
     * 标记已读。
     */
    @Override
    public JobReminderVO markRead(Long userId, Long reminderId) {
        JobReminder reminder = getUserReminderRequired(userId, reminderId);

        reminder.setIsRead(READ);

        jobReminderMapper.updateById(reminder);

        return getReminderVO(userId, reminderId);
    }

    /**
     * 取消提醒。
     */
    @Override
    public JobReminderVO cancelReminder(Long userId, Long reminderId, String reason) {
        JobReminder reminder = getUserReminderRequired(userId, reminderId);

        reminder.setReminderStatus(ReminderStatus.CANCELLED.name());
        reminder.setCancelReason(reason);
        reminder.setIsRead(READ);

        jobReminderMapper.updateById(reminder);

        return getReminderVO(userId, reminderId);
    }

    /**
     * 延期提醒。
     */
    @Override
    public JobReminderVO postponeReminder(Long userId, Long reminderId, ReminderPostponeDTO dto) {
        JobReminder reminder = getUserReminderRequired(userId, reminderId);

        if (dto.getRemindTime() == null) {
            throw new IllegalArgumentException("新的提醒时间不能为空");
        }

        reminder.setRemindTime(dto.getRemindTime());

        if (dto.getEventTime() != null) {
            reminder.setEventTime(dto.getEventTime());
        }

        /*
         * 延期后重新变为待处理和未读。
         */
        reminder.setReminderStatus(ReminderStatus.PENDING.name());
        reminder.setIsRead(UNREAD);
        reminder.setDoneTime(null);

        jobReminderMapper.updateById(reminder);

        return getReminderVO(userId, reminderId);
    }

    /**
     * 创建或更新面试提醒。
     */
    private void createOrUpdateInterviewReminder(Long userId, JobCommunicationRecord record) {
        JobReminder reminder = findByCommunicationAndType(
                userId,
                record.getId(),
                ReminderType.INTERVIEW.name()
        );

        if (reminder == null) {
            reminder = new JobReminder();
            reminder.setUserId(userId);
            reminder.setCommunicationId(record.getId());
            reminder.setApplicationId(record.getApplicationId());
            reminder.setResumeId(record.getResumeId());
            reminder.setJobId(record.getJobId());
            reminder.setReminderType(ReminderType.INTERVIEW.name());
            reminder.setIsDeleted(NOT_DELETED);
        }

        Date eventTime = record.getInterviewTime();
        Date remindTime = minusMinutes(eventTime, DEFAULT_INTERVIEW_ADVANCE_MINUTES);

        /*
         * 如果面试时间离现在已经不到 30 分钟，就立即提醒。
         */
        if (remindTime.before(new Date())) {
            remindTime = new Date();
        }

        reminder.setReminderTitle("面试提醒");
        reminder.setReminderContent(buildInterviewContent(record));
        reminder.setEventTime(eventTime);
        reminder.setRemindTime(remindTime);
        reminder.setAdvanceMinutes(DEFAULT_INTERVIEW_ADVANCE_MINUTES);
        reminder.setReminderStatus(ReminderStatus.PENDING.name());
        reminder.setIsRead(UNREAD);

        saveOrUpdate(reminder);
    }

    /**
     * 创建或更新 HR 跟进提醒。
     */
    private void createOrUpdateFollowUpReminder(Long userId, JobCommunicationRecord record) {
        JobReminder reminder = findByCommunicationAndType(
                userId,
                record.getId(),
                ReminderType.FOLLOW_UP.name()
        );

        if (reminder == null) {
            reminder = new JobReminder();
            reminder.setUserId(userId);
            reminder.setCommunicationId(record.getId());
            reminder.setApplicationId(record.getApplicationId());
            reminder.setResumeId(record.getResumeId());
            reminder.setJobId(record.getJobId());
            reminder.setReminderType(ReminderType.FOLLOW_UP.name());
            reminder.setIsDeleted(NOT_DELETED);
        }

        reminder.setReminderTitle("HR 跟进提醒");
        reminder.setReminderContent("到了你设置的 HR 跟进时间，可以查看沟通记录并决定是否继续跟进。");
        reminder.setEventTime(record.getNextFollowTime());
        reminder.setRemindTime(record.getNextFollowTime());
        reminder.setAdvanceMinutes(0);
        reminder.setReminderStatus(ReminderStatus.PENDING.name());
        reminder.setIsRead(UNREAD);

        saveOrUpdate(reminder);
    }

    /**
     * 根据沟通记录和提醒类型查询已有提醒。
     *
     * 这样做是为了避免用户多次确认面试信息时重复生成提醒。
     */
    private JobReminder findByCommunicationAndType(Long userId, Long communicationId, String reminderType) {
        if (communicationId == null) {
            return null;
        }

        return jobReminderMapper.selectOne(
                new LambdaQueryWrapper<JobReminder>()
                        .eq(JobReminder::getUserId, userId)
                        .eq(JobReminder::getCommunicationId, communicationId)
                        .eq(JobReminder::getReminderType, reminderType)
                        .eq(JobReminder::getIsDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
    }

    /**
     * 保存或更新。
     */
    private void saveOrUpdate(JobReminder reminder) {
        if (reminder.getId() == null) {
            jobReminderMapper.insert(reminder);
        } else {
            jobReminderMapper.updateById(reminder);
        }
    }

    /**
     * 构建面试提醒内容。
     */
    private String buildInterviewContent(JobCommunicationRecord record) {
        StringBuilder builder = new StringBuilder();

        builder.append("你有一场面试需要准备。");

        if (StringUtils.hasText(record.getInterviewMethod())) {
            builder.append("面试方式：").append(record.getInterviewMethod()).append("。");
        }

        if (StringUtils.hasText(record.getInterviewLocation())) {
            builder.append("地点/平台：").append(record.getInterviewLocation()).append("。");
        }

        if (StringUtils.hasText(record.getMeetingLink())) {
            builder.append("会议链接：").append(record.getMeetingLink()).append("。");
        }

        return builder.toString();
    }

    /**
     * 当前时间减去指定分钟。
     */
    private Date minusMinutes(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, -minutes);
        return calendar.getTime();
    }

    /**
     * 查询并校验提醒归属。
     */
    private JobReminder getUserReminderRequired(Long userId, Long reminderId) {
        JobReminder reminder = jobReminderMapper.selectById(reminderId);

        if (reminder == null || !Integer.valueOf(NOT_DELETED).equals(reminder.getIsDeleted())) {
            throw new IllegalArgumentException("提醒不存在");
        }

        if (!userId.equals(reminder.getUserId())) {
            throw new SecurityException("无权访问该提醒");
        }

        return reminder;
    }

    /**
     * 根据 ID 查询 VO。
     */
    private JobReminderVO getReminderVO(Long userId, Long reminderId) {
        ReminderQueryDTO queryDTO = new ReminderQueryDTO();
        queryDTO.setPageNo(1L);
        queryDTO.setPageSize(1L);

        Page<JobReminderVO> page = jobReminderMapper.selectReminderPage(
                new Page<>(1, 1),
                userId,
                queryDTO
        );

        JobReminder reminder = getUserReminderRequired(userId, reminderId);

        JobReminderVO vo = new JobReminderVO();
        vo.setId(reminder.getId());
        vo.setApplicationId(reminder.getApplicationId());
        vo.setCommunicationId(reminder.getCommunicationId());
        vo.setResumeId(reminder.getResumeId());
        vo.setJobId(reminder.getJobId());
        vo.setReminderType(reminder.getReminderType());
        vo.setReminderTitle(reminder.getReminderTitle());
        vo.setReminderContent(reminder.getReminderContent());
        vo.setEventTime(reminder.getEventTime());
        vo.setRemindTime(reminder.getRemindTime());
        vo.setAdvanceMinutes(reminder.getAdvanceMinutes());
        vo.setReminderStatus(reminder.getReminderStatus());
        vo.setIsRead(reminder.getIsRead());
        vo.setDoneTime(reminder.getDoneTime());
        vo.setCreateTime(reminder.getCreateTime());
        vo.setUpdateTime(reminder.getUpdateTime());

        fillDisplayFields(vo);

        return vo;
    }

    /**
     * 填充前端展示字段。
     */
    private void fillDisplayFields(JobReminderVO vo) {
        vo.setReminderTypeDesc(getReminderTypeDesc(vo.getReminderType()));
        vo.setReminderStatusDesc(getReminderStatusDesc(vo.getReminderStatus()));

        Date now = new Date();

        boolean overdue = ReminderStatus.PENDING.name().equals(vo.getReminderStatus())
                && vo.getRemindTime() != null
                && vo.getRemindTime().before(now);

        vo.setOverdue(overdue);

        if (vo.getRemindTime() != null) {
            long diffMillis = vo.getRemindTime().getTime() - now.getTime();
            vo.setMinutesLeft(diffMillis / 1000 / 60);
        } else {
            vo.setMinutesLeft(null);
        }
    }

    private String getReminderTypeDesc(String type) {
        if (!StringUtils.hasText(type)) {
            return "";
        }

        try {
            return ReminderType.valueOf(type).getDesc();
        } catch (Exception e) {
            return type;
        }
    }

    private String getReminderStatusDesc(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }

        try {
            return ReminderStatus.valueOf(status).getDesc();
        } catch (Exception e) {
            return status;
        }
    }

    private Long count(Long userId, String type, String status, Date start, Date end) {
        LambdaQueryWrapper<JobReminder> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(JobReminder::getUserId, userId)
                .eq(JobReminder::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(type)) {
            wrapper.eq(JobReminder::getReminderType, type);
        }

        if (StringUtils.hasText(status)) {
            wrapper.eq(JobReminder::getReminderStatus, status);
        }

        if (start != null) {
            wrapper.ge(JobReminder::getRemindTime, start);
        }

        if (end != null) {
            wrapper.le(JobReminder::getRemindTime, end);
        }

        return jobReminderMapper.selectCount(wrapper);
    }

    private Long countDue(Long userId, Date now) {
        return jobReminderMapper.selectCount(
                new LambdaQueryWrapper<JobReminder>()
                        .eq(JobReminder::getUserId, userId)
                        .eq(JobReminder::getReminderStatus, ReminderStatus.PENDING.name())
                        .le(JobReminder::getRemindTime, now)
                        .eq(JobReminder::getIsDeleted, NOT_DELETED)
        );
    }

    private Long countUnread(Long userId) {
        return jobReminderMapper.selectCount(
                new LambdaQueryWrapper<JobReminder>()
                        .eq(JobReminder::getUserId, userId)
                        .eq(JobReminder::getIsRead, UNREAD)
                        .eq(JobReminder::getIsDeleted, NOT_DELETED)
        );
    }

    private Long countToday(Long userId) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);

        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);

        return count(
                userId,
                null,
                ReminderStatus.PENDING.name(),
                start.getTime(),
                end.getTime()
        );
    }
}
