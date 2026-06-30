package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.HrReplyRecognitionRecordMapper;
import com.job.bootstrap.mapper.InterviewPrepareRecordMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.JobReminderMapper;
import com.job.bootstrap.mapper.MockInterviewStudyPlanItemMapper;
import com.job.bootstrap.mapper.MockInterviewStudyPlanMapper;
import com.job.bootstrap.mapper.MockInterviewWrongQuestionMapper;
import com.job.bootstrap.service.AgentInboxService;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.communication.HrReplyRecognitionRecord;
import com.job.common.entity.interview.InterviewPrepareRecord;
import com.job.common.entity.interview.MockInterviewStudyPlan;
import com.job.common.entity.interview.MockInterviewStudyPlanItem;
import com.job.common.entity.interview.MockInterviewWrongQuestion;
import com.job.common.entity.reminder.JobReminder;
import com.job.common.vo.agent.AgentInboxVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Agent Inbox 聚合服务实现。
 *
 * 设计说明：
 * 1. 第一版只做聚合，不新增 inbox_task 表，避免和提醒、学习计划、HR 确认记录形成双写。
 * 2. 每类业务最多取少量数据，保证首页待办加载足够轻。
 * 3. 排序统一按优先级 + 到期时间 + 创建时间，用户先看到最该处理的事情。
 */
@Service
@RequiredArgsConstructor
public class AgentInboxServiceImpl implements AgentInboxService {

    private static final int NOT_DELETED = 0;
    private static final String PRIORITY_HIGH = "HIGH";
    private static final String PRIORITY_NORMAL = "NORMAL";
    private static final String PRIORITY_LOW = "LOW";

    private final JobReminderMapper reminderMapper;
    private final HrReplyRecognitionRecordMapper recognitionRecordMapper;
    private final JobApplicationRecordMapper applicationRecordMapper;
    private final InterviewPrepareRecordMapper interviewPrepareRecordMapper;
    private final MockInterviewWrongQuestionMapper wrongQuestionMapper;
    private final MockInterviewStudyPlanMapper studyPlanMapper;
    private final MockInterviewStudyPlanItemMapper studyPlanItemMapper;

    @Override
    public AgentInboxVO getTodayInbox(Long userId) {
        List<AgentInboxVO.Item> items = new ArrayList<>();

        /*
         * 1. 聚合提醒：面试提醒、跟进提醒、自定义提醒都来自 job_reminder。
         */
        items.addAll(buildReminderItems(userId));

        /*
         * 2. 聚合 HR 回复识别待确认项：这是最需要用户确认的 Agent 动作。
         */
        items.addAll(buildHrRecognitionItems(userId));

        /*
         * 3. 聚合面试中岗位：如果已经进入面试阶段，提示用户去准备或查看 AI 面试。
         */
        items.addAll(buildInterviewApplicationItems(userId));

        /*
         * 4. 聚合最近面试准备记录：提示用户复习已生成材料。
         */
        items.addAll(buildInterviewPrepareItems(userId));

        /*
         * 5. 聚合错题本：未掌握和复习中的错题需要持续处理。
         */
        items.addAll(buildWrongQuestionItems(userId));

        /*
         * 6. 聚合学习计划：进行中的学习计划如果有未完成 item，也进入 Inbox。
         */
        items.addAll(buildStudyPlanItems(userId));

        items.sort(itemComparator());

        AgentInboxVO vo = new AgentInboxVO();
        vo.setItems(items);
        vo.setTotalCount(items.size());
        vo.setHighPriorityCount((int) items.stream().filter(item -> PRIORITY_HIGH.equals(item.getPriority())).count());
        vo.setDueCount((int) items.stream().filter(item -> item.getDueTime() != null && !item.getDueTime().after(new Date())).count());
        vo.setNormalCount((int) items.stream().filter(item -> PRIORITY_NORMAL.equals(item.getPriority())).count());
        vo.setSummaryText(buildSummaryText(vo));
        return vo;
    }

    private List<AgentInboxVO.Item> buildReminderItems(Long userId) {
        List<JobReminder> reminders = reminderMapper.selectList(
                new LambdaQueryWrapper<JobReminder>()
                        .eq(JobReminder::getUserId, userId)
                        .eq(JobReminder::getIsDeleted, NOT_DELETED)
                        .eq(JobReminder::getReminderStatus, "PENDING")
                        .orderByAsc(JobReminder::getRemindTime)
                        .last("limit 10")
        );

        return reminders.stream().map(reminder -> {
            boolean due = reminder.getRemindTime() != null && !reminder.getRemindTime().after(new Date());
            AgentInboxVO.Item item = baseItem("REMINDER", reminder.getId(), due ? PRIORITY_HIGH : PRIORITY_NORMAL);
            item.setItemTypeDesc("求职提醒");
            item.setTitle(nullToDefault(reminder.getReminderTitle(), "你有一条待处理提醒"));
            item.setDescription(nullToDefault(reminder.getReminderContent(), "点击进入跟进中心处理提醒。"));
            item.setActionText("处理提醒");
            item.setTargetPath("/follow-up");
            item.setApplicationId(reminder.getApplicationId());
            item.setCommunicationId(reminder.getCommunicationId());
            item.setJobId(reminder.getJobId());
            item.setDueTime(reminder.getRemindTime());
            item.setCreateTime(reminder.getCreateTime());
            return item;
        }).toList();
    }

    private List<AgentInboxVO.Item> buildHrRecognitionItems(Long userId) {
        List<HrReplyRecognitionRecord> records = recognitionRecordMapper.selectList(
                new LambdaQueryWrapper<HrReplyRecognitionRecord>()
                        .eq(HrReplyRecognitionRecord::getUserId, userId)
                        .eq(HrReplyRecognitionRecord::getIsDeleted, NOT_DELETED)
                        .eq(HrReplyRecognitionRecord::getConfirmStatus, "PENDING")
                        .orderByDesc(HrReplyRecognitionRecord::getCreateTime)
                        .last("limit 10")
        );

        return records.stream().map(record -> {
            AgentInboxVO.Item item = baseItem("HR_REPLY_CONFIRM", record.getId(), PRIORITY_HIGH);
            item.setItemTypeDesc("HR 回复待确认");
            item.setTitle("确认 HR 回复动作：" + nullToDefault(record.getCompanyName(), "未知公司"));
            item.setDescription(buildHrRecognitionDescription(record));
            item.setActionText("确认动作");
            item.setTargetPath(record.getCommunicationId() == null ? "/follow-up" : "/communication");
            item.setApplicationId(record.getApplicationId());
            item.setCommunicationId(record.getCommunicationId());
            item.setJobId(record.getJobId());
            item.setCompanyName(record.getCompanyName());
            item.setJobTitle(record.getJobTitle());
            item.setDueTime(record.getCreateTime());
            item.setCreateTime(record.getCreateTime());
            return item;
        }).toList();
    }

    private List<AgentInboxVO.Item> buildInterviewApplicationItems(Long userId) {
        List<JobApplicationRecord> applications = applicationRecordMapper.selectList(
                new LambdaQueryWrapper<JobApplicationRecord>()
                        .eq(JobApplicationRecord::getUserId, userId)
                        .eq(JobApplicationRecord::getIsDeleted, NOT_DELETED)
                        .eq(JobApplicationRecord::getStatus, "INTERVIEWING")
                        .orderByAsc(JobApplicationRecord::getInterviewTime)
                        .last("limit 8")
        );

        return applications.stream().map(application -> {
            AgentInboxVO.Item item = baseItem("INTERVIEW_PREPARE", application.getId(), PRIORITY_HIGH);
            item.setItemTypeDesc("面试准备");
            item.setTitle("准备面试：" + nullToDefault(application.getJobTitle(), "未命名岗位"));
            item.setDescription(nullToDefault(application.getCompanyName(), "未知公司") + " 的面试正在进行中，建议查看面试准备或开始模拟面试。");
            item.setActionText("去准备");
            item.setTargetPath("/application");
            item.setApplicationId(application.getId());
            item.setJobId(application.getJobId());
            item.setCompanyName(application.getCompanyName());
            item.setJobTitle(application.getJobTitle());
            item.setDueTime(application.getInterviewTime());
            item.setCreateTime(application.getCreateTime());
            return item;
        }).toList();
    }

    private List<AgentInboxVO.Item> buildInterviewPrepareItems(Long userId) {
        List<InterviewPrepareRecord> records = interviewPrepareRecordMapper.selectList(
                new LambdaQueryWrapper<InterviewPrepareRecord>()
                        .eq(InterviewPrepareRecord::getUserId, userId)
                        .eq(InterviewPrepareRecord::getIsDeleted, NOT_DELETED)
                        .orderByDesc(InterviewPrepareRecord::getCreateTime)
                        .last("limit 5")
        );

        return records.stream().map(record -> {
            AgentInboxVO.Item item = baseItem("PREPARE_REVIEW", record.getId(), PRIORITY_NORMAL);
            item.setItemTypeDesc("面试材料复习");
            item.setTitle("复习面试材料：" + nullToDefault(record.getJobTitle(), "未命名岗位"));
            item.setDescription(nullToDefault(record.getSummary(), "AI 已生成面试准备材料，建议面试前复习一遍。"));
            item.setActionText("查看材料");
            item.setTargetPath("/application");
            item.setApplicationId(record.getApplicationId());
            item.setJobId(record.getJobId());
            item.setCompanyName(record.getCompanyName());
            item.setJobTitle(record.getJobTitle());
            item.setDueTime(record.getCreateTime());
            item.setCreateTime(record.getCreateTime());
            return item;
        }).toList();
    }

    private List<AgentInboxVO.Item> buildWrongQuestionItems(Long userId) {
        List<MockInterviewWrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewWrongQuestion>()
                        .eq(MockInterviewWrongQuestion::getUserId, userId)
                        .eq(MockInterviewWrongQuestion::getIsDeleted, NOT_DELETED)
                        .in(MockInterviewWrongQuestion::getMasteryStatus, "UNMASTERED", "REVIEWING")
                        .orderByDesc(MockInterviewWrongQuestion::getWrongCount)
                        .orderByDesc(MockInterviewWrongQuestion::getUpdateTime)
                        .last("limit 8")
        );

        return wrongQuestions.stream().map(question -> {
            AgentInboxVO.Item item = baseItem("WRONG_QUESTION_REVIEW", question.getId(), PRIORITY_NORMAL);
            item.setItemTypeDesc("错题复习");
            item.setTitle("复习错题：" + shortText(question.getQuestionContent(), 24));
            item.setDescription(nullToDefault(question.getWrongReason(), "这道题还没有完全掌握，建议复习知识点并标记掌握状态。"));
            item.setActionText("去错题本");
            item.setTargetPath("/wrong-questions");
            item.setJobId(question.getJobId());
            item.setDueTime(question.getUpdateTime());
            item.setCreateTime(question.getCreateTime());
            return item;
        }).toList();
    }

    private List<AgentInboxVO.Item> buildStudyPlanItems(Long userId) {
        List<MockInterviewStudyPlan> plans = studyPlanMapper.selectList(
                new LambdaQueryWrapper<MockInterviewStudyPlan>()
                        .eq(MockInterviewStudyPlan::getUserId, userId)
                        .eq(MockInterviewStudyPlan::getIsDeleted, NOT_DELETED)
                        .eq(MockInterviewStudyPlan::getStatus, "ACTIVE")
                        .orderByDesc(MockInterviewStudyPlan::getCreateTime)
                        .last("limit 3")
        );

        List<AgentInboxVO.Item> items = new ArrayList<>();
        for (MockInterviewStudyPlan plan : plans) {
            List<MockInterviewStudyPlanItem> pendingItems = studyPlanItemMapper.selectList(
                    new LambdaQueryWrapper<MockInterviewStudyPlanItem>()
                            .eq(MockInterviewStudyPlanItem::getUserId, userId)
                            .eq(MockInterviewStudyPlanItem::getPlanId, plan.getId())
                            .eq(MockInterviewStudyPlanItem::getIsDeleted, NOT_DELETED)
                            .eq(MockInterviewStudyPlanItem::getCompletionStatus, "PENDING")
                            .orderByAsc(MockInterviewStudyPlanItem::getDayNo)
                            .last("limit 1")
            );

            if (pendingItems.isEmpty()) {
                continue;
            }

            MockInterviewStudyPlanItem pending = pendingItems.get(0);
            AgentInboxVO.Item item = baseItem("LEARNING_PLAN", pending.getId(), PRIORITY_NORMAL);
            item.setItemTypeDesc("学习计划");
            item.setTitle("继续学习：" + nullToDefault(pending.getTitle(), plan.getPlanTitle()));
            item.setDescription(nullToDefault(pending.getLearningGoal(), "你的学习计划还有未完成任务。"));
            item.setActionText("去学习");
            item.setTargetPath("/learning-plan");
            item.setDueTime(pending.getCreateTime());
            item.setCreateTime(pending.getCreateTime());
            items.add(item);
        }
        return items;
    }

    private AgentInboxVO.Item baseItem(String type, Long sourceId, String priority) {
        AgentInboxVO.Item item = new AgentInboxVO.Item();
        item.setItemType(type);
        item.setItemKey(type + "_" + sourceId);
        item.setSourceId(sourceId);
        item.setPriority(priority);
        return item;
    }

    private Comparator<AgentInboxVO.Item> itemComparator() {
        return Comparator
                .comparingInt((AgentInboxVO.Item item) -> priorityRank(item.getPriority()))
                .thenComparing(item -> item.getDueTime() == null ? new Date(Long.MAX_VALUE) : item.getDueTime())
                .thenComparing(item -> item.getCreateTime() == null ? new Date(0) : item.getCreateTime(), Comparator.reverseOrder());
    }

    private int priorityRank(String priority) {
        if (PRIORITY_HIGH.equals(priority)) {
            return 0;
        }
        if (PRIORITY_NORMAL.equals(priority)) {
            return 1;
        }
        return 2;
    }

    private String buildSummaryText(AgentInboxVO vo) {
        if (vo.getTotalCount() == 0) {
            return "今天暂时没有必须处理的 Agent 待办，可以继续浏览岗位或练习面试。";
        }
        return "今天有 " + vo.getTotalCount() + " 个 Agent 待办，其中 " + vo.getHighPriorityCount() + " 个高优先级。";
    }

    private String buildHrRecognitionDescription(HrReplyRecognitionRecord record) {
        String jobText = nullToDefault(record.getJobTitle(), "未知岗位");
        String reason = nullToDefault(record.getReason(), "AI 已识别 HR 回复，需要你确认是否更新求职进度。");
        return jobText + "：" + reason;
    }

    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String shortText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "未命名题目";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
