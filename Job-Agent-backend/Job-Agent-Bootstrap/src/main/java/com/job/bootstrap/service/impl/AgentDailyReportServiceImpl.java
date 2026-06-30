package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.AgentDailyReportRecordMapper;
import com.job.bootstrap.mapper.JobUserMapper;
import com.job.bootstrap.service.AgentDailyReportService;
import com.job.bootstrap.service.AgentInboxService;
import com.job.bootstrap.service.JobMailSenderService;
import com.job.common.entity.agent.AgentDailyReportRecord;
import com.job.common.entity.user.JobUser;
import com.job.common.vo.agent.AgentDailyReportVO;
import com.job.common.vo.agent.AgentInboxVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Agent 主动日报服务实现。
 *
 * 设计说明：
 * 1. 日报内容复用 Agent Inbox，避免再写一套“待办扫描”逻辑导致两边口径不一致。
 * 2. userId + reportDate 做幂等：定时任务重复执行或用户手动点击生成，都只会更新当天同一条日报。
 * 3. 邮件发送失败不会回滚日报记录，因为“日报已生成”和“邮件发送失败”是两个不同事实，都应该被保存。
 */
@Service
@RequiredArgsConstructor
public class AgentDailyReportServiceImpl implements AgentDailyReportService {

    private static final int NOT_DELETED = 0;
    private static final int STATUS_NORMAL = 1;
    private static final String EMAIL_PENDING = "PENDING";
    private static final String EMAIL_SENT = "SENT";
    private static final String EMAIL_SKIPPED = "SKIPPED";
    private static final String EMAIL_FAILED = "FAILED";
    private static final int MAX_SCHEDULE_USER_COUNT = 500;

    private final AgentDailyReportRecordMapper reportRecordMapper;
    private final JobUserMapper jobUserMapper;
    private final AgentInboxService agentInboxService;
    private final AgentDailyReportComposer reportComposer;
    private final JobMailSenderService mailSenderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDailyReportVO generateForUser(Long userId, Date reportDate, boolean sendEmail) {
        Date normalizedReportDate = normalizeDate(reportDate);
        JobUser user = jobUserMapper.selectById(userId);
        if (user == null || !Integer.valueOf(NOT_DELETED).equals(user.getIsDeleted())) {
            throw new IllegalArgumentException("用户不存在，无法生成 Agent 日报");
        }

        /*
         * 1. 从现有 Inbox 聚合结果读取今日待办，日报第一版不重复扫描业务表。
         */
        AgentInboxVO inbox = agentInboxService.getTodayInbox(userId);
        AgentDailyReportComposer.ComposeResult composeResult = reportComposer.compose(inbox);

        /*
         * 2. 按用户 + 日期查找已有日报，有则覆盖当天快照，没有则插入。
         */
        AgentDailyReportRecord record = findByUserAndDate(userId, normalizedReportDate);
        if (record == null) {
            record = new AgentDailyReportRecord();
            record.setUserId(userId);
            record.setReportDate(normalizedReportDate);
            record.setIsDeleted(NOT_DELETED);
            record.setCreateTime(new Date());
        }

        fillReportSnapshot(record, composeResult, inbox);
        record.setUpdateTime(new Date());
        record.setEmailStatus(EMAIL_PENDING);
        record.setEmailError(null);
        record.setEmailTo(trimToNull(user.getEmail()));

        if (record.getId() == null) {
            reportRecordMapper.insert(record);
        } else {
            reportRecordMapper.updateById(record);
        }

        /*
         * 3. 根据调用方决定是否发送邮件。手动生成和定时生成都可以复用同一条逻辑。
         */
        if (sendEmail) {
            sendReportEmail(record, user);
        } else {
            record.setEmailStatus(EMAIL_SKIPPED);
            record.setEmailError("本次只生成日报，未请求发送邮件");
            reportRecordMapper.updateById(record);
        }

        return toVO(record);
    }

    @Override
    public List<AgentDailyReportVO> listRecent(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return reportRecordMapper.selectList(
                        new LambdaQueryWrapper<AgentDailyReportRecord>()
                                .eq(AgentDailyReportRecord::getUserId, userId)
                                .eq(AgentDailyReportRecord::getIsDeleted, NOT_DELETED)
                                .orderByDesc(AgentDailyReportRecord::getReportDate)
                                .orderByDesc(AgentDailyReportRecord::getCreateTime)
                                .last("limit " + safeLimit)
                )
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public int generateTodayForActiveUsers() {
        List<JobUser> users = jobUserMapper.selectList(
                new LambdaQueryWrapper<JobUser>()
                        .eq(JobUser::getIsDeleted, NOT_DELETED)
                        .eq(JobUser::getStatus, STATUS_NORMAL)
                        .isNotNull(JobUser::getEmail)
                        .last("limit " + MAX_SCHEDULE_USER_COUNT)
        );

        int successCount = 0;
        Date today = new Date();
        for (JobUser user : users) {
            if (!StringUtils.hasText(user.getEmail())) {
                continue;
            }
            try {
                generateForUser(user.getId(), today, true);
                successCount++;
            } catch (Exception ignored) {
                /*
                 * 单个用户失败不能阻断整批日报。
                 * 失败详情会在 generateForUser 内尽量写入 emailStatus/emailError；
                 * 如果失败发生在查询用户或 Inbox 阶段，则由调度日志记录整体异常即可。
                 */
            }
        }
        return successCount;
    }

    private void fillReportSnapshot(
            AgentDailyReportRecord record,
            AgentDailyReportComposer.ComposeResult composeResult,
            AgentInboxVO inbox
    ) {
        record.setReportTitle(composeResult.title());
        record.setSummaryText(composeResult.summary());
        record.setContentText(composeResult.content());
        record.setContentJson(composeResult.contentJson());
        record.setInboxTotalCount(defaultInt(inbox.getTotalCount()));
        record.setHighPriorityCount(defaultInt(inbox.getHighPriorityCount()));
        record.setDueCount(defaultInt(inbox.getDueCount()));
    }

    private void sendReportEmail(AgentDailyReportRecord record, JobUser user) {
        String email = trimToNull(user.getEmail());
        if (!StringUtils.hasText(email)) {
            record.setEmailStatus(EMAIL_SKIPPED);
            record.setEmailError("用户未配置邮箱");
            reportRecordMapper.updateById(record);
            return;
        }

        try {
            /*
             * 邮件正文直接使用纯文本日报，保证不同邮箱客户端都能正常展示。
             */
            mailSenderService.sendText(email, record.getReportTitle(), record.getContentText());
            record.setEmailTo(email);
            record.setEmailStatus(EMAIL_SENT);
            record.setEmailError(null);
            record.setSendTime(new Date());
        } catch (Exception exception) {
            record.setEmailStatus(EMAIL_FAILED);
            record.setEmailError(shortText(exception.getMessage(), 1000));
        }
        reportRecordMapper.updateById(record);
    }

    private AgentDailyReportRecord findByUserAndDate(Long userId, Date reportDate) {
        return reportRecordMapper.selectOne(
                new LambdaQueryWrapper<AgentDailyReportRecord>()
                        .eq(AgentDailyReportRecord::getUserId, userId)
                        .eq(AgentDailyReportRecord::getReportDate, reportDate)
                        .eq(AgentDailyReportRecord::getIsDeleted, NOT_DELETED)
                        .last("limit 1")
        );
    }

    private AgentDailyReportVO toVO(AgentDailyReportRecord record) {
        AgentDailyReportVO vo = new AgentDailyReportVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setReportDate(record.getReportDate());
        vo.setReportTitle(record.getReportTitle());
        vo.setSummaryText(record.getSummaryText());
        vo.setContentText(record.getContentText());
        vo.setInboxTotalCount(record.getInboxTotalCount());
        vo.setHighPriorityCount(record.getHighPriorityCount());
        vo.setDueCount(record.getDueCount());
        vo.setEmailTo(record.getEmailTo());
        vo.setEmailStatus(record.getEmailStatus());
        vo.setEmailError(record.getEmailError());
        vo.setSendTime(record.getSendTime());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private Date normalizeDate(Date date) {
        Date source = date == null ? new Date() : date;
        LocalDate localDate = source.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String shortText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
