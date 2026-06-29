package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.AgentObservationAlertRecordMapper;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.mapper.AiModelCallLogMapper;
import com.job.bootstrap.mapper.AiModelConfigMapper;
import com.job.bootstrap.mapper.AiPromptVersionMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.JobPositionMapper;
import com.job.bootstrap.mapper.JobReminderMapper;
import com.job.bootstrap.mapper.JobResumeMapper;
import com.job.bootstrap.mapper.JobUserMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.mapper.RagChunkMapper;
import com.job.bootstrap.mapper.RagDocumentMapper;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.AdminDashboardService;
import com.job.common.entity.agent.AgentObservationAlertRecord;
import com.job.common.entity.agent.AgentTraceLog;
import com.job.common.entity.ai.AiModelCallLog;
import com.job.common.entity.ai.AiModelConfig;
import com.job.common.entity.ai.AiPromptVersion;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.interview.MockInterviewSession;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.rag.RagChunk;
import com.job.common.entity.rag.RagDocument;
import com.job.common.entity.reminder.JobReminder;
import com.job.common.entity.resume.JobResume;
import com.job.common.entity.user.JobUser;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.vo.admin.AdminFollowUpAgentItemVO;
import com.job.common.vo.admin.AdminDashboardMetricVO;
import com.job.common.vo.admin.AdminDashboardOverviewVO;
import com.job.common.vo.admin.AdminDashboardPendingItemVO;
import com.job.common.vo.admin.AdminDashboardSystemItemVO;
import com.job.enums.ReminderStatus;
import com.job.enums.ReminderType;
import com.job.enums.WorkflowTaskStatus;
import com.job.enums.WorkflowTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * 后台首页看板聚合服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int NOT_DELETED = 0;
    private static final int POSITION_PUBLISHED = 1;
    private static final int POSITION_DRAFT = 0;
    private static final String STATUS_PARSED = "PARSED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String APPLICATION_APPLIED = "APPLIED";
    private static final String APPLICATION_INTERVIEWING = "INTERVIEWING";

    private final JobUserMapper userMapper;
    private final JobPositionMapper positionMapper;
    private final JobResumeMapper resumeMapper;
    private final JobApplicationRecordMapper applicationRecordMapper;
    private final JobReminderMapper reminderMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final AgentTraceLogMapper traceLogMapper;
    private final AiModelCallLogMapper modelCallLogMapper;
    private final RagDocumentMapper ragDocumentMapper;
    private final RagChunkMapper ragChunkMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final AiPromptVersionMapper promptVersionMapper;
    private final MockInterviewSessionMapper mockInterviewSessionMapper;
    private final AgentObservationAlertRecordMapper alertRecordMapper;

    /**
     * 查询后台首页看板数据。
     *
     * 方法步骤:
     * 1. 先计算今天的时间边界，所有“今日”指标都复用同一组时间，避免不同查询时间不一致。
     * 2. 分别查询核心业务表总量和今日增量，组成顶部四张指标卡。
     * 3. 查询需要管理员关注的异常或待处理数据，组成“今日待处理”列表。
     * 4. 查询 AI/RAG/面试等系统能力数据，组成右侧系统状态说明。
     *
     * @return 后台首页看板数据
     */
    @Override
    public AdminDashboardOverviewVO getOverview() {
        Date todayStart = startOfToday();
        Date tomorrowStart = startOfTomorrow();

        long userCount = countUsers(null, null);
        long todayUserCount = countUsers(todayStart, tomorrowStart);
        long publishedPositionCount = countPublishedPositions(null, null);
        long todayPositionCount = countPublishedPositions(todayStart, tomorrowStart);
        long resumeCount = countResumes(null, null, null);
        long parsedResumeCount = countResumes(STATUS_PARSED, null, null);
        long traceCount = countTraceLogs(null, null, null);
        long todayTraceCount = countTraceLogs(null, todayStart, tomorrowStart);

        AdminDashboardOverviewVO overview = new AdminDashboardOverviewVO();
        overview.getMetrics().add(new AdminDashboardMetricVO("注册用户", userCount, "今日新增 " + todayUserCount));
        overview.getMetrics().add(new AdminDashboardMetricVO("在招岗位", publishedPositionCount, "今日发布 " + todayPositionCount));
        overview.getMetrics().add(new AdminDashboardMetricVO("简历数量", resumeCount, "已解析 " + parsedResumeCount));
        overview.getMetrics().add(new AdminDashboardMetricVO("Agent 调用", traceCount, "今日调用 " + todayTraceCount));

        overview.getPendingItems().add(buildPendingItem("岗位审核", countDraftPositions(), " 个待发布岗位", "warning"));
        overview.getPendingItems().add(buildPendingItem("Agent 异常", countTraceLogs(STATUS_FAILED, todayStart, tomorrowStart), " 条失败 Agent Trace", "danger"));
        overview.getPendingItems().add(buildPendingItem("模型异常", countModelCalls(STATUS_FAILED, todayStart, tomorrowStart), " 条失败模型调用", "danger"));
        overview.getPendingItems().add(buildPendingItem("告警处理", countOpenAlerts(), " 条未处理告警", "warning"));

        long ragDocumentCount = countActiveRagDocuments();
        long ragChunkCount = countActiveRagChunks();
        overview.getSystemItems().add(new AdminDashboardSystemItemVO("RAG 知识库", ragDocumentCount + " 篇文档 / " + ragChunkCount + " 个切片"));
        overview.getSystemItems().add(new AdminDashboardSystemItemVO("模型配置", countActiveModels() + " 个启用模型"));
        overview.getSystemItems().add(new AdminDashboardSystemItemVO("Prompt 版本", countPublishedPromptVersions() + " 个已发布版本"));
        overview.getSystemItems().add(new AdminDashboardSystemItemVO("模拟面试", countMockInterviewSessions(null, null, null) + " 场累计 / 今日 " + countMockInterviewSessions(null, todayStart, tomorrowStart)));
        overview.getSystemItems().add(new AdminDashboardSystemItemVO("已完成面试", countMockInterviewSessions(STATUS_FINISHED, null, null) + " 场"));
        fillFollowUpAgentItems(overview, todayStart, tomorrowStart);
        return overview;
    }

    /**
     * 构造待处理事项。
     */
    private AdminDashboardPendingItemVO buildPendingItem(String title, long count, String suffix, String level) {
        String content = count > 0 ? count + suffix : "暂无需要处理的数据";
        return new AdminDashboardPendingItemVO(title, content, count > 0 ? level : "success");
    }

    /**
     * 统计用户数量。
     */
    private long countUsers(Date startTime, Date endTime) {
        LambdaQueryWrapper<JobUser> wrapper = new LambdaQueryWrapper<JobUser>()
                .eq(JobUser::getIsDeleted, NOT_DELETED);
        betweenCreateTime(wrapper, JobUser::getCreateTime, startTime, endTime);
        return userMapper.selectCount(wrapper);
    }

    /**
     * 统计已发布岗位数量。
     */
    private long countPublishedPositions(Date startTime, Date endTime) {
        LambdaQueryWrapper<JobPosition> wrapper = new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getIsDeleted, NOT_DELETED)
                .eq(JobPosition::getStatus, POSITION_PUBLISHED);
        betweenCreateTime(wrapper, JobPosition::getCreateTime, startTime, endTime);
        return positionMapper.selectCount(wrapper);
    }

    /**
     * 统计草稿或待发布岗位数量。
     */
    private long countDraftPositions() {
        return positionMapper.selectCount(new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getIsDeleted, NOT_DELETED)
                .eq(JobPosition::getStatus, POSITION_DRAFT));
    }

    /**
     * 统计简历数量。
     */
    private long countResumes(String status, Date startTime, Date endTime) {
        LambdaQueryWrapper<JobResume> wrapper = new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getIsDeleted, NOT_DELETED);
        if (status != null) {
            wrapper.eq(JobResume::getStatus, status);
        }
        betweenCreateTime(wrapper, JobResume::getCreateTime, startTime, endTime);
        return resumeMapper.selectCount(wrapper);
    }

    /**
     * 统计 Agent Trace 数量。
     */
    private long countTraceLogs(String status, Date startTime, Date endTime) {
        LambdaQueryWrapper<AgentTraceLog> wrapper = new LambdaQueryWrapper<AgentTraceLog>()
                .eq(AgentTraceLog::getIsDeleted, NOT_DELETED);
        if (status != null) {
            wrapper.eq(AgentTraceLog::getStatus, status);
        }
        betweenCreateTime(wrapper, AgentTraceLog::getCreateTime, startTime, endTime);
        return traceLogMapper.selectCount(wrapper);
    }

    /**
     * 统计模型调用数量。
     */
    private long countModelCalls(String status, Date startTime, Date endTime) {
        LambdaQueryWrapper<AiModelCallLog> wrapper = new LambdaQueryWrapper<AiModelCallLog>()
                .eq(AiModelCallLog::getIsDeleted, NOT_DELETED);
        if (status != null) {
            wrapper.eq(AiModelCallLog::getStatus, status);
        }
        betweenCreateTime(wrapper, AiModelCallLog::getCreateTime, startTime, endTime);
        return modelCallLogMapper.selectCount(wrapper);
    }

    private long countActiveRagDocuments() {
        return ragDocumentMapper.selectCount(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getIsDeleted, NOT_DELETED)
                .eq(RagDocument::getStatus, STATUS_ACTIVE));
    }

    private long countActiveRagChunks() {
        return ragChunkMapper.selectCount(new LambdaQueryWrapper<RagChunk>()
                .eq(RagChunk::getIsDeleted, NOT_DELETED)
                .eq(RagChunk::getStatus, STATUS_ACTIVE));
    }

    private long countActiveModels() {
        return modelConfigMapper.selectCount(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getIsDeleted, NOT_DELETED)
                .eq(AiModelConfig::getStatus, STATUS_ACTIVE));
    }

    private long countPublishedPromptVersions() {
        return promptVersionMapper.selectCount(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getIsDeleted, NOT_DELETED)
                .eq(AiPromptVersion::getStatus, STATUS_PUBLISHED));
    }

    /**
     * 统计模拟面试数量。
     */
    private long countMockInterviewSessions(String status, Date startTime, Date endTime) {
        LambdaQueryWrapper<MockInterviewSession> wrapper = new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getIsDeleted, NOT_DELETED);
        if (status != null) {
            wrapper.eq(MockInterviewSession::getStatus, status);
        }
        betweenCreateTime(wrapper, MockInterviewSession::getCreateTime, startTime, endTime);
        return mockInterviewSessionMapper.selectCount(wrapper);
    }

    /**
     * 填充求职跟进 Agent 看板。
     *
     * 方法步骤：
     * 1. 从求职进度表统计当前已投递和面试中的记录，判断 Agent 可介入的业务规模。
     * 2. 从提醒表统计今日待提醒和面试提醒，判断自动提醒是否生成。
     * 3. 从工作流任务表统计面试邮件任务，判断邮件通知链路是否健康。
     */
    private void fillFollowUpAgentItems(AdminDashboardOverviewVO overview, Date todayStart, Date tomorrowStart) {
        overview.getFollowUpAgentItems().add(new AdminFollowUpAgentItemVO(
                "已投递",
                countApplications(APPLICATION_APPLIED, null, null),
                "等待 HR 反馈的求职记录",
                "info"
        ));
        overview.getFollowUpAgentItems().add(new AdminFollowUpAgentItemVO(
                "面试中",
                countApplications(APPLICATION_INTERVIEWING, null, null),
                "已进入面试阶段的求职记录",
                "warning"
        ));
        overview.getFollowUpAgentItems().add(new AdminFollowUpAgentItemVO(
                "今日待提醒",
                countPendingReminders(null, todayStart, tomorrowStart),
                "今天需要用户处理的提醒",
                "warning"
        ));
        overview.getFollowUpAgentItems().add(new AdminFollowUpAgentItemVO(
                "面试提醒",
                countPendingReminders(ReminderType.INTERVIEW.name(), null, null),
                "待触达的面试准备提醒",
                "success"
        ));
        overview.getFollowUpAgentItems().add(new AdminFollowUpAgentItemVO(
                "邮件任务失败",
                countInterviewEmailTasks(WorkflowTaskStatus.FAILED_FINAL.name(), null, null),
                "最终失败的面试通知邮件任务",
                "danger"
        ));
        overview.getFollowUpAgentItems().add(new AdminFollowUpAgentItemVO(
                "今日邮件任务",
                countInterviewEmailTasks(null, todayStart, tomorrowStart),
                "今天创建的面试通知邮件任务",
                "info"
        ));
    }

    /**
     * 统计求职进度数量。
     */
    private long countApplications(String status, Date startTime, Date endTime) {
        LambdaQueryWrapper<JobApplicationRecord> wrapper = new LambdaQueryWrapper<JobApplicationRecord>()
                .eq(JobApplicationRecord::getIsDeleted, NOT_DELETED);
        if (status != null) {
            wrapper.eq(JobApplicationRecord::getStatus, status);
        }
        betweenCreateTime(wrapper, JobApplicationRecord::getCreateTime, startTime, endTime);
        return applicationRecordMapper.selectCount(wrapper);
    }

    /**
     * 统计待处理提醒数量。
     */
    private long countPendingReminders(String reminderType, Date startTime, Date endTime) {
        LambdaQueryWrapper<JobReminder> wrapper = new LambdaQueryWrapper<JobReminder>()
                .eq(JobReminder::getIsDeleted, NOT_DELETED)
                .eq(JobReminder::getReminderStatus, ReminderStatus.PENDING.name());
        if (reminderType != null) {
            wrapper.eq(JobReminder::getReminderType, reminderType);
        }
        if (startTime != null) {
            wrapper.ge(JobReminder::getRemindTime, startTime);
        }
        if (endTime != null) {
            wrapper.lt(JobReminder::getRemindTime, endTime);
        }
        return reminderMapper.selectCount(wrapper);
    }

    /**
     * 统计面试邮件通知工作流任务数量。
     */
    private long countInterviewEmailTasks(String status, Date startTime, Date endTime) {
        LambdaQueryWrapper<WorkflowTask> wrapper = new LambdaQueryWrapper<WorkflowTask>()
                .eq(WorkflowTask::getIsDeleted, NOT_DELETED)
                .eq(WorkflowTask::getTaskType, WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name());
        if (status != null) {
            wrapper.eq(WorkflowTask::getStatus, status);
        }
        betweenCreateTime(wrapper, WorkflowTask::getCreateTime, startTime, endTime);
        return workflowTaskMapper.selectCount(wrapper);
    }

    private long countOpenAlerts() {
        return alertRecordMapper.selectCount(new LambdaQueryWrapper<AgentObservationAlertRecord>()
                .eq(AgentObservationAlertRecord::getIsDeleted, NOT_DELETED)
                .eq(AgentObservationAlertRecord::getStatus, STATUS_OPEN));
    }

    /**
     * 给查询条件追加 create_time 半开区间。
     *
     * @param wrapper 查询条件
     * @param column createTime 字段引用
     * @param startTime 开始时间，包含
     * @param endTime 结束时间，不包含
     * @param <T> 实体类型
     */
    private <T> void betweenCreateTime(
            LambdaQueryWrapper<T> wrapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Date> column,
            Date startTime,
            Date endTime
    ) {
        if (startTime != null) {
            wrapper.ge(column, startTime);
        }
        if (endTime != null) {
            wrapper.lt(column, endTime);
        }
    }

    private Date startOfToday() {
        return Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Date startOfTomorrow() {
        return Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
