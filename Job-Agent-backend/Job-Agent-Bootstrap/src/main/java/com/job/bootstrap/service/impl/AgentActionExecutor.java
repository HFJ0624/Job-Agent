package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.JobReminderService;
import com.job.bootstrap.service.MockInterviewLearningPlanService;
import com.job.bootstrap.service.MockInterviewWrongQuestionService;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.common.dto.interview.MockInterviewStudyPlanItemStatusDTO;
import com.job.common.dto.interview.MockInterviewWrongQuestionStatusDTO;
import com.job.common.dto.reminder.ReminderCreateDTO;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.entity.agent.AgentActionItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Agent 行动执行器。
 *
 * 说明：
 * 1. 执行器只处理已经明确允许联动的 actionType，未知 actionType 直接失败，避免 AI 建议误改业务数据。
 * 2. 行动项状态由 AgentActionCenterService 统一更新，这里只负责调用具体业务服务。
 * 3. 跳转查看、人工沟通这类动作继续保持 MANUAL_CONFIRM，不进入执行器。
 */
@Component
@RequiredArgsConstructor
public class AgentActionExecutor {

    private static final String ACTION_REMINDER_CREATE = "REMINDER_CREATE";
    private static final String ACTION_REMINDER_DONE = "REMINDER_DONE";
    private static final String ACTION_LEARNING_PLAN_DONE = "LEARNING_PLAN_DONE";
    private static final String ACTION_WRONG_QUESTION_REVIEWED = "WRONG_QUESTION_REVIEWED";
    private static final String ACTION_WRONG_QUESTION_MASTERED = "WRONG_QUESTION_MASTERED";
    private static final String ACTION_WORKFLOW_TASK_CREATE = "WORKFLOW_TASK_CREATE";

    private final JobReminderService reminderService;
    private final MockInterviewLearningPlanService learningPlanService;
    private final MockInterviewWrongQuestionService wrongQuestionService;
    private final WorkflowTaskService workflowTaskService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行行动项联动。
     *
     * @param item 已校验归属当前用户的行动项
     */
    public void execute(AgentActionItem item) {
        if (ACTION_REMINDER_CREATE.equals(item.getActionType())) {
            reminderService.createReminder(item.getUserId(), parsePayload(item.getActionPayload(), ReminderCreateDTO.class));
            return;
        }
        if (ACTION_REMINDER_DONE.equals(item.getActionType())) {
            reminderService.markDone(item.getUserId(), requireBizId(item));
            return;
        }
        if (ACTION_LEARNING_PLAN_DONE.equals(item.getActionType())) {
            MockInterviewStudyPlanItemStatusDTO dto = new MockInterviewStudyPlanItemStatusDTO();
            dto.setCompletionStatus("DONE");
            learningPlanService.updateItemStatus(item.getUserId(), requireBizId(item), dto);
            return;
        }
        if (ACTION_WRONG_QUESTION_REVIEWED.equals(item.getActionType())
                || ACTION_WRONG_QUESTION_MASTERED.equals(item.getActionType())) {
            MockInterviewWrongQuestionStatusDTO dto = new MockInterviewWrongQuestionStatusDTO();
            dto.setMasteryStatus(ACTION_WRONG_QUESTION_MASTERED.equals(item.getActionType()) ? "MASTERED" : "REVIEWING");
            wrongQuestionService.updateMasteryStatus(item.getUserId(), requireBizId(item), dto);
            return;
        }
        if (ACTION_WORKFLOW_TASK_CREATE.equals(item.getActionType())) {
            createWorkflowTask(item);
            return;
        }
        throw new IllegalArgumentException("当前行动类型暂不支持联动执行：" + item.getActionType());
    }

    private void createWorkflowTask(AgentActionItem item) {
        /*
         * 步骤：
         * 1. 从行动项 payload 解析工作流创建参数。
         * 2. 强制使用行动项归属用户作为任务 userId，避免 payload 越权创建其他用户任务。
         * 3. 如果 payload 没有 bizId，则复用行动项 bizId，便于邮件通知等任务定位业务记录。
         * 4. 这里只负责入队，真正执行由工作流调度器按重试、状态机和失败恢复规则处理。
         */
        WorkflowTaskCreateDTO request = parsePayload(item.getActionPayload(), WorkflowTaskCreateDTO.class);
        request.setUserId(item.getUserId());
        if (request.getBizId() == null) {
            request.setBizId(item.getBizId());
        }
        workflowTaskService.createTask(request);
    }

    private Long requireBizId(AgentActionItem item) {
        if (item.getBizId() == null) {
            throw new IllegalArgumentException("行动项缺少业务 ID，无法执行：" + item.getActionType());
        }
        return item.getBizId();
    }

    private <T> T parsePayload(String payload, Class<T> targetType) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("行动项缺少执行参数");
        }
        try {
            return objectMapper.readValue(payload, targetType);
        } catch (Exception exception) {
            throw new IllegalArgumentException("行动项执行参数解析失败：" + exception.getMessage(), exception);
        }
    }
}
