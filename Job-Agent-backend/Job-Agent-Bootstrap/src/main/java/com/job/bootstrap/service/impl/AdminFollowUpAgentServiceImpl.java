package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.AgentFollowUpRuleMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.JobReminderMapper;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.AdminFollowUpAgentService;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.common.dto.agent.AgentFollowUpApplicationQueryDTO;
import com.job.common.dto.agent.AgentFollowUpRuleQueryDTO;
import com.job.common.dto.agent.AgentFollowUpRuleSaveDTO;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.entity.agent.AgentFollowUpRule;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.reminder.JobReminder;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.vo.agent.AgentFollowUpApplicationVO;
import com.job.common.vo.agent.AgentFollowUpRuleVO;
import com.job.enums.AgentFollowUpRuleStatus;
import com.job.enums.AgentFollowUpRuleType;
import com.job.enums.ReminderStatus;
import com.job.enums.ReminderType;
import com.job.enums.WorkflowTaskStatus;
import com.job.enums.WorkflowTaskType;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台求职跟进 Agent 服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminFollowUpAgentServiceImpl implements AdminFollowUpAgentService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int ENABLED = 1;
    private static final int UNREAD = 0;
    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final int DEFAULT_RETRY_INTERVAL_SECONDS = 300;
    private static final int DEFAULT_SCAN_LIMIT = 200;

    private final AgentFollowUpRuleMapper agentFollowUpRuleMapper;
    private final JobApplicationRecordMapper jobApplicationRecordMapper;
    private final JobReminderMapper jobReminderMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final WorkflowTaskService workflowTaskService;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<AgentFollowUpApplicationVO> pageApplications(AgentFollowUpApplicationQueryDTO query) {
        AgentFollowUpApplicationQueryDTO safeQuery = query == null ? new AgentFollowUpApplicationQueryDTO() : query;
        Page<JobApplicationRecord> page = new Page<>(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<JobApplicationRecord> wrapper = new LambdaQueryWrapper<JobApplicationRecord>()
                .eq(JobApplicationRecord::getIsDeleted, NOT_DELETED);

        if (safeQuery.getUserId() != null) {
            wrapper.eq(JobApplicationRecord::getUserId, safeQuery.getUserId());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(JobApplicationRecord::getStatus, safeQuery.getStatus().trim());
        }
        if (StringUtils.hasText(safeQuery.getKeyword())) {
            String keyword = safeQuery.getKeyword().trim();
            wrapper.and(item -> item.like(JobApplicationRecord::getCompanyName, keyword)
                    .or()
                    .like(JobApplicationRecord::getJobTitle, keyword)
                    .or()
                    .like(JobApplicationRecord::getHrName, keyword));
        }
        if (Boolean.TRUE.equals(safeQuery.getFailedEmailOnly())) {
            Set<Long> failedApplicationIds = loadFailedEmailApplicationIds(safeQuery.getUserId());
            if (failedApplicationIds.isEmpty()) {
                return emptyApplicationPage(page);
            }
            wrapper.in(JobApplicationRecord::getId, failedApplicationIds);
        }
        wrapper.orderByDesc(JobApplicationRecord::getUpdateTime);

        return jobApplicationRecordMapper.selectPage(page, wrapper).convert(this::buildApplicationVO);
    }

    @Override
    public IPage<AgentFollowUpRuleVO> pageRules(AgentFollowUpRuleQueryDTO query) {
        AgentFollowUpRuleQueryDTO safeQuery = query == null ? new AgentFollowUpRuleQueryDTO() : query;
        Page<AgentFollowUpRule> page = new Page<>(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<AgentFollowUpRule> wrapper = new LambdaQueryWrapper<AgentFollowUpRule>()
                .eq(AgentFollowUpRule::getIsDeleted, NOT_DELETED);
        if (StringUtils.hasText(safeQuery.getRuleName())) {
            wrapper.like(AgentFollowUpRule::getRuleName, safeQuery.getRuleName().trim());
        }
        if (StringUtils.hasText(safeQuery.getRuleType())) {
            wrapper.eq(AgentFollowUpRule::getRuleType, safeQuery.getRuleType().trim());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(AgentFollowUpRule::getStatus, safeQuery.getStatus().trim());
        }
        wrapper.orderByDesc(AgentFollowUpRule::getCreateTime);
        return agentFollowUpRuleMapper.selectPage(page, wrapper).convert(AgentFollowUpRuleVO::from);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentFollowUpRuleVO createRule(AgentFollowUpRuleSaveDTO request) {
        ensureRuleCodeUnique(null, request.getRuleCode());
        AgentFollowUpRule rule = new AgentFollowUpRule();
        fillRule(rule, request);
        Date now = new Date();
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        rule.setIsDeleted(NOT_DELETED);
        agentFollowUpRuleMapper.insert(rule);
        return AgentFollowUpRuleVO.from(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentFollowUpRuleVO updateRule(Long id, AgentFollowUpRuleSaveDTO request) {
        AgentFollowUpRule rule = loadRule(id);
        ensureRuleCodeUnique(id, request.getRuleCode());
        fillRule(rule, request);
        rule.setUpdateTime(new Date());
        agentFollowUpRuleMapper.updateById(rule);
        return AgentFollowUpRuleVO.from(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        AgentFollowUpRule rule = loadRule(id);
        rule.setIsDeleted(DELETED);
        rule.setUpdateTime(new Date());
        agentFollowUpRuleMapper.updateById(rule);
    }

    /**
     * 扫描启用规则并创建提醒。
     *
     * 步骤：
     * 1. 只加载 ENABLED 且未删除的规则，避免后台禁用后仍继续触发。
     * 2. 第一版只扫描适合定时处理的规则：投递未反馈、面试后复盘。
     * 3. 每条规则最多扫描 200 条候选投递记录，防止一次调度拖垮数据库。
     * 4. 创建提醒前做幂等检查，已有同类提醒时直接跳过。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanEnabledRules() {
        List<AgentFollowUpRule> rules = agentFollowUpRuleMapper.selectList(
                new LambdaQueryWrapper<AgentFollowUpRule>()
                        .eq(AgentFollowUpRule::getStatus, AgentFollowUpRuleStatus.ENABLED.name())
                        .eq(AgentFollowUpRule::getIsDeleted, NOT_DELETED)
        );
        int createdCount = 0;
        for (AgentFollowUpRule rule : rules) {
            AgentFollowUpRuleType ruleType = parseRuleType(rule.getRuleType());
            if (AgentFollowUpRuleType.APPLICATION_NO_FEEDBACK.equals(ruleType)) {
                createdCount += scanNoFeedbackRule(rule);
            } else if (AgentFollowUpRuleType.INTERVIEW_AFTER_REVIEW.equals(ruleType)) {
                createdCount += scanInterviewReviewRule(rule);
            }
        }
        return createdCount;
    }

    private AgentFollowUpApplicationVO buildApplicationVO(JobApplicationRecord application) {
        AgentFollowUpApplicationVO vo = AgentFollowUpApplicationVO.from(application);
        if (vo == null || application.getId() == null) {
            return vo;
        }

        vo.setReminderCount(countReminders(application, null));
        vo.setPendingReminderCount(countReminders(application, ReminderStatus.PENDING.name()));
        vo.setEmailTaskCount(countEmailTasks(application, null));
        vo.setFailedEmailTaskCount(countEmailTasks(application, WorkflowTaskStatus.FAILED_FINAL.name()));

        WorkflowTask latestTask = workflowTaskMapper.selectOne(
                new LambdaQueryWrapper<WorkflowTask>()
                        .eq(WorkflowTask::getTaskType, WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name())
                        .eq(WorkflowTask::getBizId, application.getId())
                        .eq(WorkflowTask::getUserId, application.getUserId())
                        .eq(WorkflowTask::getIsDeleted, NOT_DELETED)
                        .orderByDesc(WorkflowTask::getCreateTime)
                        .last("LIMIT 1")
        );
        if (latestTask != null) {
            vo.setLatestEmailTaskStatus(latestTask.getStatus());
            vo.setLatestEmailTaskTime(latestTask.getCreateTime());
        }
        return vo;
    }

    private long countReminders(JobApplicationRecord application, String status) {
        LambdaQueryWrapper<JobReminder> wrapper = new LambdaQueryWrapper<JobReminder>()
                .eq(JobReminder::getApplicationId, application.getId())
                .eq(JobReminder::getUserId, application.getUserId())
                .eq(JobReminder::getIsDeleted, NOT_DELETED);
        if (StringUtils.hasText(status)) {
            wrapper.eq(JobReminder::getReminderStatus, status);
        }
        Long count = jobReminderMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private long countEmailTasks(JobApplicationRecord application, String status) {
        LambdaQueryWrapper<WorkflowTask> wrapper = new LambdaQueryWrapper<WorkflowTask>()
                .eq(WorkflowTask::getTaskType, WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name())
                .eq(WorkflowTask::getBizId, application.getId())
                .eq(WorkflowTask::getUserId, application.getUserId())
                .eq(WorkflowTask::getIsDeleted, NOT_DELETED);
        if (StringUtils.hasText(status)) {
            wrapper.eq(WorkflowTask::getStatus, status);
        }
        Long count = workflowTaskMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private Set<Long> loadFailedEmailApplicationIds(Long userId) {
        LambdaQueryWrapper<WorkflowTask> wrapper = new LambdaQueryWrapper<WorkflowTask>()
                .select(WorkflowTask::getBizId)
                .eq(WorkflowTask::getTaskType, WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name())
                .eq(WorkflowTask::getStatus, WorkflowTaskStatus.FAILED_FINAL.name())
                .eq(WorkflowTask::getIsDeleted, NOT_DELETED)
                .isNotNull(WorkflowTask::getBizId);
        if (userId != null) {
            wrapper.eq(WorkflowTask::getUserId, userId);
        }
        return workflowTaskMapper.selectList(wrapper)
                .stream()
                .map(WorkflowTask::getBizId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private IPage<AgentFollowUpApplicationVO> emptyApplicationPage(Page<JobApplicationRecord> sourcePage) {
        Page<AgentFollowUpApplicationVO> emptyPage = new Page<>(sourcePage.getCurrent(), sourcePage.getSize());
        emptyPage.setTotal(0);
        emptyPage.setRecords(Collections.emptyList());
        return emptyPage;
    }

    private int scanNoFeedbackRule(AgentFollowUpRule rule) {
        Date cutoff = addMinutes(new Date(), -Math.max(0, safeInt(rule.getDelayMinutes())));
        List<JobApplicationRecord> applications = jobApplicationRecordMapper.selectList(
                new LambdaQueryWrapper<JobApplicationRecord>()
                        .eq(JobApplicationRecord::getIsDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(rule.getTriggerStatus()), JobApplicationRecord::getStatus, rule.getTriggerStatus())
                        .le(JobApplicationRecord::getApplyTime, cutoff)
                        .orderByAsc(JobApplicationRecord::getApplyTime)
                        .last("LIMIT " + DEFAULT_SCAN_LIMIT)
        );
        int created = 0;
        for (JobApplicationRecord application : applications) {
            if (createReminderIfAbsent(rule, application, new Date(), new Date())) {
                created++;
            }
        }
        return created;
    }

    private int scanInterviewReviewRule(AgentFollowUpRule rule) {
        Date cutoff = addMinutes(new Date(), -Math.max(0, safeInt(rule.getDelayMinutes())));
        List<JobApplicationRecord> applications = jobApplicationRecordMapper.selectList(
                new LambdaQueryWrapper<JobApplicationRecord>()
                        .eq(JobApplicationRecord::getIsDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(rule.getTriggerStatus()), JobApplicationRecord::getStatus, rule.getTriggerStatus())
                        .isNotNull(JobApplicationRecord::getInterviewTime)
                        .le(JobApplicationRecord::getInterviewTime, cutoff)
                        .orderByAsc(JobApplicationRecord::getInterviewTime)
                        .last("LIMIT " + DEFAULT_SCAN_LIMIT)
        );
        int created = 0;
        for (JobApplicationRecord application : applications) {
            if (createReminderIfAbsent(rule, application, application.getInterviewTime(), new Date())) {
                created++;
            }
        }
        return created;
    }

    private boolean createReminderIfAbsent(AgentFollowUpRule rule, JobApplicationRecord application, Date eventTime, Date remindTime) {
        String reminderType = safeReminderType(rule.getReminderType());
        String title = safeText(rule.getReminderTitle(), rule.getRuleName());
        Long count = jobReminderMapper.selectCount(
                new LambdaQueryWrapper<JobReminder>()
                        .eq(JobReminder::getUserId, application.getUserId())
                        .eq(JobReminder::getApplicationId, application.getId())
                        .eq(JobReminder::getReminderType, reminderType)
                        .eq(JobReminder::getReminderTitle, title)
                        .eq(JobReminder::getIsDeleted, NOT_DELETED)
        );
        if (count != null && count > 0) {
            return false;
        }

        JobReminder reminder = new JobReminder();
        reminder.setUserId(application.getUserId());
        reminder.setApplicationId(application.getId());
        reminder.setResumeId(application.getResumeId());
        reminder.setJobId(application.getJobId());
        reminder.setReminderType(reminderType);
        reminder.setReminderTitle(title);
        reminder.setReminderContent(renderTemplate(rule.getReminderTemplate(), application));
        reminder.setEventTime(eventTime);
        reminder.setRemindTime(remindTime);
        reminder.setAdvanceMinutes(Math.max(0, Math.abs(safeInt(rule.getDelayMinutes()))));
        reminder.setReminderStatus(ReminderStatus.PENDING.name());
        reminder.setIsRead(UNREAD);
        reminder.setIsDeleted(NOT_DELETED);
        reminder.setCreateTime(new Date());
        reminder.setUpdateTime(new Date());
        jobReminderMapper.insert(reminder);

        if (ENABLED == safeInt(rule.getWorkflowEnabled()) && ENABLED == safeInt(rule.getEmailEnabled())) {
            createEmailTaskIfAbsent(rule, application);
        }
        return true;
    }

    private void createEmailTaskIfAbsent(AgentFollowUpRule rule, JobApplicationRecord application) {
        Long count = workflowTaskMapper.selectCount(
                new LambdaQueryWrapper<WorkflowTask>()
                        .eq(WorkflowTask::getTaskType, WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name())
                        .eq(WorkflowTask::getBizId, application.getId())
                        .eq(WorkflowTask::getUserId, application.getUserId())
                        .in(WorkflowTask::getStatus,
                                WorkflowTaskStatus.PENDING.name(),
                                WorkflowTaskStatus.RUNNING.name(),
                                WorkflowTaskStatus.FAILED_RETRYABLE.name(),
                                WorkflowTaskStatus.SUCCESS.name())
                        .eq(WorkflowTask::getIsDeleted, NOT_DELETED)
        );
        if (count != null && count > 0) {
            return;
        }

        WorkflowTaskCreateDTO request = new WorkflowTaskCreateDTO();
        request.setTaskType(WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name());
        request.setBizId(application.getId());
        request.setUserId(application.getUserId());
        request.setRequestJson(toJson(buildEmailPayload(rule, application)));
        request.setMaxRetryCount(safePositive(rule.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT));
        request.setRetryIntervalSeconds(safePositive(rule.getRetryIntervalSeconds(), DEFAULT_RETRY_INTERVAL_SECONDS));
        workflowTaskService.createTask(request);
    }

    private Map<String, Object> buildEmailPayload(AgentFollowUpRule rule, JobApplicationRecord application) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleCode", rule.getRuleCode());
        payload.put("applicationId", application.getId());
        payload.put("jobId", application.getJobId());
        payload.put("resumeId", application.getResumeId());
        payload.put("companyName", application.getCompanyName());
        payload.put("jobTitle", application.getJobTitle());
        payload.put("interviewTime", application.getInterviewTime());
        return payload;
    }

    private void fillRule(AgentFollowUpRule rule, AgentFollowUpRuleSaveDTO request) {
        AgentFollowUpRuleType ruleType = parseRuleType(request.getRuleType());
        AgentFollowUpRuleStatus status = parseRuleStatus(safeText(request.getStatus(), AgentFollowUpRuleStatus.ENABLED.name()));

        rule.setRuleCode(requireText(request.getRuleCode(), "规则编码不能为空"));
        rule.setRuleName(requireText(request.getRuleName(), "规则名称不能为空"));
        rule.setRuleType(ruleType.name());
        rule.setTriggerStatus(trimToNull(request.getTriggerStatus()));
        rule.setDelayMinutes(request.getDelayMinutes() == null ? 0 : request.getDelayMinutes());
        rule.setReminderType(safeReminderType(request.getReminderType()));
        rule.setReminderTitle(trimToNull(request.getReminderTitle()));
        rule.setReminderTemplate(trimToNull(request.getReminderTemplate()));
        rule.setEmailEnabled(booleanInt(request.getEmailEnabled()));
        rule.setWorkflowEnabled(booleanInt(request.getWorkflowEnabled()));
        rule.setMaxRetryCount(safePositive(request.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT));
        rule.setRetryIntervalSeconds(safePositive(request.getRetryIntervalSeconds(), DEFAULT_RETRY_INTERVAL_SECONDS));
        rule.setStatus(status.name());
        rule.setRemark(trimToNull(request.getRemark()));
    }

    private AgentFollowUpRule loadRule(Long id) {
        AgentFollowUpRule rule = agentFollowUpRuleMapper.selectById(id);
        if (rule == null || Objects.equals(rule.getIsDeleted(), DELETED)) {
            throw new BizException("求职跟进规则不存在");
        }
        return rule;
    }

    private void ensureRuleCodeUnique(Long id, String ruleCode) {
        Long count = agentFollowUpRuleMapper.selectCount(
                new LambdaQueryWrapper<AgentFollowUpRule>()
                        .eq(AgentFollowUpRule::getRuleCode, requireText(ruleCode, "规则编码不能为空"))
                        .eq(AgentFollowUpRule::getIsDeleted, NOT_DELETED)
                        .ne(id != null, AgentFollowUpRule::getId, id)
        );
        if (count != null && count > 0) {
            throw new BizException("规则编码已存在");
        }
    }

    private String renderTemplate(String template, JobApplicationRecord application) {
        String content = safeText(template, "请关注该岗位的最新求职进展。");
        return content
                .replace("{companyName}", safe(application.getCompanyName()))
                .replace("{jobTitle}", safe(application.getJobTitle()))
                .replace("{hrName}", safe(application.getHrName()))
                .replace("{status}", safe(application.getStatus()));
    }

    private AgentFollowUpRuleType parseRuleType(String value) {
        try {
            return AgentFollowUpRuleType.valueOf(requireText(value, "规则类型不能为空"));
        } catch (Exception exception) {
            throw new BizException("不支持的求职跟进规则类型：" + value);
        }
    }

    private AgentFollowUpRuleStatus parseRuleStatus(String value) {
        try {
            return AgentFollowUpRuleStatus.valueOf(requireText(value, "规则状态不能为空"));
        } catch (Exception exception) {
            throw new BizException("不支持的求职跟进规则状态：" + value);
        }
    }

    private String safeReminderType(String value) {
        if (!StringUtils.hasText(value)) {
            return ReminderType.FOLLOW_UP.name();
        }
        try {
            return ReminderType.valueOf(value.trim()).name();
        } catch (Exception exception) {
            throw new BizException("不支持的提醒类型：" + value);
        }
    }

    private Date addMinutes(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, minutes);
        return calendar.getTime();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int booleanInt(Integer value) {
        return value == null || value <= 0 ? 0 : 1;
    }

    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
