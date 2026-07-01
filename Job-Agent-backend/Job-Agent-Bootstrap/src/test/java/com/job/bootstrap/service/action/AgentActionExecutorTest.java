package com.job.bootstrap.service.action;

import com.job.bootstrap.service.JobReminderService;
import com.job.bootstrap.service.MockInterviewLearningPlanService;
import com.job.bootstrap.service.MockInterviewWrongQuestionService;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.bootstrap.service.impl.AgentActionExecutor;
import com.job.common.dto.interview.MockInterviewStudyPlanItemStatusDTO;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.entity.agent.AgentActionItem;
import com.job.common.vo.workflow.WorkflowTaskVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 行动执行器测试。
 */
class AgentActionExecutorTest {

    @Test
    void shouldExecuteLearningPlanDoneAction() {
        JobReminderService reminderService = mock(JobReminderService.class);
        MockInterviewLearningPlanService learningPlanService = mock(MockInterviewLearningPlanService.class);
        MockInterviewWrongQuestionService wrongQuestionService = mock(MockInterviewWrongQuestionService.class);
        WorkflowTaskService workflowTaskService = mock(WorkflowTaskService.class);
        AgentActionExecutor executor = new AgentActionExecutor(
                reminderService,
                learningPlanService,
                wrongQuestionService,
                workflowTaskService
        );

        AgentActionItem item = new AgentActionItem();
        item.setUserId(9L);
        item.setActionType("LEARNING_PLAN_DONE");
        item.setBizId(18L);

        Long workflowTaskId = executor.execute(item);

        verify(learningPlanService).updateItemStatus(
                eq(9L),
                eq(18L),
                any(MockInterviewStudyPlanItemStatusDTO.class)
        );
        assertEquals(null, workflowTaskId);
    }

    @Test
    void shouldCreateWorkflowTaskWhenActionTypeIsWorkflowTaskCreate() {
        JobReminderService reminderService = mock(JobReminderService.class);
        MockInterviewLearningPlanService learningPlanService = mock(MockInterviewLearningPlanService.class);
        MockInterviewWrongQuestionService wrongQuestionService = mock(MockInterviewWrongQuestionService.class);
        WorkflowTaskService workflowTaskService = mock(WorkflowTaskService.class);
        AgentActionExecutor executor = new AgentActionExecutor(
                reminderService,
                learningPlanService,
                wrongQuestionService,
                workflowTaskService
        );
        when(workflowTaskService.createTask(any(WorkflowTaskCreateDTO.class)))
                .thenReturn(WorkflowTaskVO.builder().id(66L).build());

        AgentActionItem item = new AgentActionItem();
        item.setUserId(9L);
        item.setActionType("WORKFLOW_TASK_CREATE");
        item.setActionPayload("""
                {
                  "taskType": "INTERVIEW_EMAIL_NOTIFY",
                  "bizId": 18,
                  "userId": 9,
                  "maxRetryCount": 3
                }
                """);

        Long workflowTaskId = executor.execute(item);

        verify(workflowTaskService).createTask(any(WorkflowTaskCreateDTO.class));
        assertEquals(66L, workflowTaskId);
    }
}
