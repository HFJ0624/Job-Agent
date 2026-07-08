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
import com.job.common.vo.workflow.WorkflowTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Agent 行动执行器，负责将确认后的行动项联动到具体业务服务。
 *
 * <p>核心职责：
 * 接收 AgentActionCenterService 校验过的行动项，按 actionType 分发到对应业务服务
 * （提醒、学习计划、错题、工作流任务等），把“AI 建议”转化为真实业务数据变更。
 * 执行器本身不更新行动项状态，状态由 AgentActionCenterService 统一回写。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Action 子模块（行动执行层）。</p>
 *
 * <p>主要调用链：
 * AgentActionCenterService.confirmAndExecute -> AgentActionExecutor.execute
 * -> 按 actionType 分发 -> JobReminderService / MockInterviewLearningPlanService
 * / MockInterviewWrongQuestionService / WorkflowTaskService</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>调用方为 AgentActionCenterService，后者负责权限校验、状态机与回写；</li>
 *   <li>执行器只处理已明确允许联动的 actionType，未知 actionType 直接抛异常，避免 AI 建议误改业务数据；</li>
 *   <li>同步动作（提醒、学习计划、错题）返回 null，异步工作流任务返回 taskId 供行动项回写进度。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 执行器只处理已经明确允许联动的 actionType，未知 actionType 直接失败，避免 AI 建议误改业务数据。
 * 2. 行动项状态由 AgentActionCenterService 统一更新，这里只负责调用具体业务服务。
 * 3. 返回值用于把异步工作流任务 ID 回写到行动项，普通同步动作返回 null。</p>
 *
 * 作者: hfj
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
     * 执行行动项联动，按 actionType 分发到对应业务服务。
     *
     * <p>核心处理流程：
     * 1. REMINDER_CREATE：解析 payload 为 ReminderCreateDTO 并创建提醒；
     * 2. REMINDER_DONE：根据 bizId 把提醒标记为已完成；
     * 3. LEARNING_PLAN_DONE：把学习计划条目标记为 DONE；
     * 4. WRONG_QUESTION_REVIEWED / WRONG_QUESTION_MASTERED：更新错题掌握状态；
     * 5. WORKFLOW_TASK_CREATE：创建异步工作流任务并返回 taskId；
     * 6. 未识别 actionType 抛 IllegalArgumentException，避免 AI 建议误改业务数据。</p>
     *
     * @param item 已校验归属当前用户的行动项，包含 actionType、bizId、payload
     * @return 新建的工作流任务 ID，用于回写行动项进度；同步动作返回 null
     * @throws IllegalArgumentException actionType 未支持或 bizId 缺失时抛出
     */
    public Long execute(AgentActionItem item) {
        if (ACTION_REMINDER_CREATE.equals(item.getActionType())) {
            reminderService.createReminder(item.getUserId(), parsePayload(item.getActionPayload(), ReminderCreateDTO.class));
            return null;
        }
        if (ACTION_REMINDER_DONE.equals(item.getActionType())) {
            reminderService.markDone(item.getUserId(), requireBizId(item));
            return null;
        }
        if (ACTION_LEARNING_PLAN_DONE.equals(item.getActionType())) {
            MockInterviewStudyPlanItemStatusDTO dto = new MockInterviewStudyPlanItemStatusDTO();
            dto.setCompletionStatus("DONE");
            learningPlanService.updateItemStatus(item.getUserId(), requireBizId(item), dto);
            return null;
        }
        if (ACTION_WRONG_QUESTION_REVIEWED.equals(item.getActionType())
                || ACTION_WRONG_QUESTION_MASTERED.equals(item.getActionType())) {
            MockInterviewWrongQuestionStatusDTO dto = new MockInterviewWrongQuestionStatusDTO();
            dto.setMasteryStatus(ACTION_WRONG_QUESTION_MASTERED.equals(item.getActionType()) ? "MASTERED" : "REVIEWING");
            wrongQuestionService.updateMasteryStatus(item.getUserId(), requireBizId(item), dto);
            return null;
        }
        if (ACTION_WORKFLOW_TASK_CREATE.equals(item.getActionType())) {
            return createWorkflowTask(item);
        }
        throw new IllegalArgumentException("当前行动类型暂不支持联动执行：" + item.getActionType());
    }

    /**
     * 创建异步工作流任务，强制使用行动项归属用户作为任务 userId，避免 payload 越权。
     *
     * <p>核心处理流程：
     * 1. 从行动项 payload 解析工作流创建参数；
     * 2. 强制使用行动项归属用户作为任务 userId，避免 payload 越权创建其他用户任务；
     * 3. 如果 payload 没有 bizId，则复用行动项 bizId，便于邮件通知等任务定位业务记录；
     * 4. 创建任务后返回 taskId，让行动项可以展示异步执行进度。</p>
     *
     * @param item 工作流任务创建行动项
     * @return 新建工作流任务 ID，创建失败或返回 null 时由调用方处理
     */
    private Long createWorkflowTask(AgentActionItem item) {
        /*
         * 步骤：
         * 1. 从行动项 payload 解析工作流创建参数。
         * 2. 强制使用行动项归属用户作为任务 userId，避免 payload 越权创建其他用户任务。
         * 3. 如果 payload 没有 bizId，则复用行动项 bizId，便于邮件通知等任务定位业务记录。
         * 4. 创建任务后返回 taskId，让行动项可以展示异步执行进度。
         */
        WorkflowTaskCreateDTO request = parsePayload(item.getActionPayload(), WorkflowTaskCreateDTO.class);
        request.setUserId(item.getUserId());
        if (request.getBizId() == null) {
            request.setBizId(item.getBizId());
        }
        WorkflowTaskVO task = workflowTaskService.createTask(request);
        return task == null ? null : task.getId();
    }

    /**
     * 校验行动项 bizId 非空，缺失时抛异常阻断执行。
     *
     * @param item 待执行行动项
     * @return 业务 ID
     * @throws IllegalArgumentException bizId 为空时抛出，避免业务服务找不到目标记录
     */
    private Long requireBizId(AgentActionItem item) {
        if (item.getBizId() == null) {
            throw new IllegalArgumentException("行动项缺少业务 ID，无法执行：" + item.getActionType());
        }
        return item.getBizId();
    }

    /**
     * 将行动项 payload JSON 反序列化为目标 DTO，失败时抛 IllegalArgumentException 阻断执行。
     *
     * @param payload    行动项 payload JSON 字符串
     * @param targetType 目标 DTO 类型
     * @param <T>        DTO 泛型
     * @return 反序列化后的 DTO 实例
     * @throws IllegalArgumentException payload 为空或解析失败时抛出
     */
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
