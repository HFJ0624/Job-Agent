package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.FrontFollowUpAgentService;
import com.job.bootstrap.service.JobApplicationService;
import com.job.bootstrap.service.JobReminderService;
import com.job.common.dto.application.JobApplicationQueryDTO;
import com.job.common.dto.reminder.ReminderQueryDTO;
import com.job.common.vo.agent.FrontFollowUpActionVO;
import com.job.common.vo.agent.FrontFollowUpApplicationVO;
import com.job.common.vo.agent.FrontFollowUpCenterVO;
import com.job.common.vo.application.JobApplicationVO;
import com.job.common.vo.reminder.JobReminderVO;
import com.job.common.vo.reminder.ReminderPageVO;
import com.job.enums.ReminderStatus;
import com.job.enums.ReminderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户端求职跟进 Agent 聚合服务实现，负责把投递记录与提醒聚合成统一跟进中心视图。
 *
 * <p>核心职责：
 * 复用 JobApplicationService 与 JobReminderService 的分页查询能力，按 applicationId 把待处理提醒
 * 归属到对应投递记录上，并根据投递状态、提醒类型与时间生成建议动作（如“准备面试”“跟进 HR”），
 * 让前端只负责展示与跳转，不再自己拼接业务判断逻辑。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent FollowUp 子模块（用户端跟进中心）。</p>
 *
 * <p>主要调用链：
 * 前端跟进中心 -> FrontFollowUpAgentService.getCenter
 * -> JobApplicationService.pageApplications（投递分页）
 * -> JobReminderService.pageReminders（待处理提醒分页）
 * -> 按 applicationId 分组后聚合
 * -> buildSuggestedActions / fillPriority 生成建议动作与优先级</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>复用现有求职记录分页，避免新增一套投递查询逻辑；</li>
 *   <li>复用现有提醒分页，只取待处理提醒并按 applicationId 分组；</li>
 *   <li>按投递状态、提醒类型和时间生成建议动作，前端只展示不拼接；</li>
 *   <li>建议动作 targetPath 指向已有页面，不引入新路由。</li>
 * </ul></p>
 *
 * 作者: hfj
 */
@Service
@RequiredArgsConstructor
public class FrontFollowUpAgentServiceImpl implements FrontFollowUpAgentService {

    private static final int CENTER_APPLICATION_LIMIT = 50;
    private static final int CENTER_REMINDER_LIMIT = 100;

    private final JobApplicationService jobApplicationService;
    private final JobReminderService jobReminderService;

    /**
     * 查询用户端求职跟进中心聚合视图。
     *
     * <p>核心处理流程：
     * 1. 复用现有求职记录分页，避免新增一套投递查询逻辑；
     * 2. 复用现有提醒分页，只取待处理提醒并按 applicationId 分组；
     * 3. 按投递状态、提醒类型和时间生成建议动作；
     * 4. 返回聚合后的视图，前端只负责展示和跳转，不再自己拼接业务判断。</p>
     *
     * @param userId 当前用户 ID
     * @return 跟进中心 VO，包含投递统计、提醒统计、投递列表（含提醒与建议动作）
     */
    @Override
    public FrontFollowUpCenterVO getCenter(Long userId) {
        JobApplicationQueryDTO applicationQuery = new JobApplicationQueryDTO();
        applicationQuery.setPageNum(1L);
        applicationQuery.setPageSize((long) CENTER_APPLICATION_LIMIT);
        IPage<JobApplicationVO> applicationPage = jobApplicationService.pageApplications(userId, applicationQuery);

        ReminderQueryDTO reminderQuery = new ReminderQueryDTO();
        reminderQuery.setPageNo(1L);
        reminderQuery.setPageSize((long) CENTER_REMINDER_LIMIT);
        reminderQuery.setReminderStatus(ReminderStatus.PENDING.name());
        ReminderPageVO reminderPage = jobReminderService.pageReminders(userId, reminderQuery);

        Map<Long, List<JobReminderVO>> remindersByApplication = reminderPage.getRecords()
                .stream()
                .filter(item -> item.getApplicationId() != null)
                .collect(Collectors.groupingBy(JobReminderVO::getApplicationId));

        FrontFollowUpCenterVO center = new FrontFollowUpCenterVO();
        center.setApplicationStats(jobApplicationService.getStats(userId));
        center.setReminderStats(jobReminderService.getStats(userId));
        center.setApplications(applicationPage.getRecords()
                .stream()
                .map(application -> buildApplication(application, remindersByApplication.getOrDefault(application.getId(), List.of())))
                .toList());
        return center;
    }

    private FrontFollowUpApplicationVO buildApplication(JobApplicationVO application, List<JobReminderVO> reminders) {
        FrontFollowUpApplicationVO vo = new FrontFollowUpApplicationVO();
        vo.setApplication(application);
        vo.setPendingReminders(reminders);
        vo.setSuggestedActions(buildSuggestedActions(application, reminders));
        fillPriority(vo, reminders);
        return vo;
    }

    private List<FrontFollowUpActionVO> buildSuggestedActions(JobApplicationVO application, List<JobReminderVO> reminders) {
        List<FrontFollowUpActionVO> actions = reminders.stream()
                .map(reminder -> buildReminderAction(application, reminder))
                .toList();
        if (!actions.isEmpty()) {
            return actions;
        }

        if ("INTERVIEWING".equals(application.getStatus())) {
            return List.of(
                    FrontFollowUpActionVO.of(
                            "PREPARE_INTERVIEW",
                            "准备这场面试",
                            "当前岗位已经进入面试中，可以先生成面试准备材料，再进行模拟面试。",
                            "去准备",
                            "HIGH",
                            "/application"
                    ),
                    FrontFollowUpActionVO.of(
                            "START_MOCK_INTERVIEW",
                            "做一次模拟面试",
                            "用当前岗位和简历启动 AI 模拟面试，提前发现薄弱点。",
                            "开始模拟",
                            "NORMAL",
                            "/ai-interview"
                    )
            );
        }

        if ("APPLIED".equals(application.getStatus())) {
            return List.of(FrontFollowUpActionVO.of(
                    "FOLLOW_UP_HR",
                    "关注投递反馈",
                    "如果投递已经过了一段时间，可以准备一条礼貌的 HR 跟进话术。",
                    "查看沟通",
                    "NORMAL",
                    "/communication"
            ));
        }

        return List.of(FrontFollowUpActionVO.of(
                "KEEP_TRACKING",
                "保持跟进",
                "继续维护投递状态，后续 Agent 会根据状态和提醒给出下一步建议。",
                "查看进度",
                "LOW",
                "/application"
        ));
    }

    private FrontFollowUpActionVO buildReminderAction(JobApplicationVO application, JobReminderVO reminder) {
        if (ReminderType.INTERVIEW.name().equals(reminder.getReminderType())) {
            return FrontFollowUpActionVO.of(
                    "PREPARE_INTERVIEW",
                    safeTitle(reminder, "面试准备提醒"),
                    safeDescription(reminder, "这场面试即将开始，建议先查看面试准备材料。"),
                    "去准备",
                    reminder.getOverdue() ? "HIGH" : "NORMAL",
                    "/application"
            );
        }
        if (ReminderType.FOLLOW_UP.name().equals(reminder.getReminderType())) {
            return FrontFollowUpActionVO.of(
                    "FOLLOW_UP_HR",
                    safeTitle(reminder, "HR 跟进提醒"),
                    safeDescription(reminder, "到了跟进时间，可以查看沟通记录并决定是否继续跟进。"),
                    "去跟进",
                    reminder.getOverdue() ? "HIGH" : "NORMAL",
                    "/communication"
            );
        }
        return FrontFollowUpActionVO.of(
                "HANDLE_REMINDER",
                safeTitle(reminder, "处理提醒"),
                safeDescription(reminder, "你有一条待处理提醒。"),
                "查看提醒",
                reminder.getOverdue() ? "HIGH" : "NORMAL",
                "/follow-up"
        );
    }

    private void fillPriority(FrontFollowUpApplicationVO vo, List<JobReminderVO> reminders) {
        boolean hasOverdue = reminders.stream().anyMatch(item -> Boolean.TRUE.equals(item.getOverdue()));
        boolean hasInterviewReminder = reminders.stream().anyMatch(item -> ReminderType.INTERVIEW.name().equals(item.getReminderType()));
        JobApplicationVO application = vo.getApplication();

        if (hasOverdue) {
            vo.setPriority("HIGH");
            vo.setPriorityReason("存在已到期提醒，需要尽快处理");
            return;
        }
        if (hasInterviewReminder || "INTERVIEWING".equals(application.getStatus())) {
            vo.setPriority("HIGH");
            vo.setPriorityReason("岗位处于面试阶段，建议优先准备");
            return;
        }
        if ("APPLIED".equals(application.getStatus()) && application.getApplyTime() != null && daysSince(application.getApplyTime()) >= 3) {
            vo.setPriority("NORMAL");
            vo.setPriorityReason("投递超过 3 天，可以考虑跟进反馈");
            return;
        }
        vo.setPriority("LOW");
        vo.setPriorityReason("暂无紧急事项，保持状态更新即可");
    }

    private long daysSince(Date date) {
        return Math.max(0, (System.currentTimeMillis() - date.getTime()) / 1000 / 60 / 60 / 24);
    }

    private String safeTitle(JobReminderVO reminder, String defaultValue) {
        return reminder.getReminderTitle() == null || reminder.getReminderTitle().isBlank()
                ? defaultValue
                : reminder.getReminderTitle();
    }

    private String safeDescription(JobReminderVO reminder, String defaultValue) {
        return reminder.getReminderContent() == null || reminder.getReminderContent().isBlank()
                ? defaultValue
                : reminder.getReminderContent();
    }
}
