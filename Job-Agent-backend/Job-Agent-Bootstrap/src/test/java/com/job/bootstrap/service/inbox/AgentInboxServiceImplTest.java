package com.job.bootstrap.service.inbox;

import com.job.bootstrap.mapper.AgentInboxActionRecordMapper;
import com.job.bootstrap.mapper.HrReplyRecognitionRecordMapper;
import com.job.bootstrap.mapper.InterviewPrepareRecordMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.JobReminderMapper;
import com.job.bootstrap.mapper.MockInterviewStudyPlanItemMapper;
import com.job.bootstrap.mapper.MockInterviewStudyPlanMapper;
import com.job.bootstrap.mapper.MockInterviewWrongQuestionMapper;
import com.job.bootstrap.service.JobReminderService;
import com.job.bootstrap.service.MockInterviewLearningPlanService;
import com.job.bootstrap.service.MockInterviewWrongQuestionService;
import com.job.bootstrap.service.impl.AgentInboxServiceImpl;
import com.job.common.entity.communication.HrReplyRecognitionRecord;
import com.job.common.vo.agent.AgentInboxVO;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent Inbox 聚合服务测试。
 *
 * 说明：
 * 1. Inbox 不直接拥有业务数据，而是从提醒、HR 回复识别、错题本等模块聚合今日待办。
 * 2. 这里用 Mock Mapper 固定返回，验证服务会把待确认 HR 回复转换成高优先级待办。
 */
class AgentInboxServiceImplTest {

    @Test
    void shouldBuildHighPriorityInboxItemForPendingHrRecognition() {
        JobReminderMapper reminderMapper = mock(JobReminderMapper.class);
        AgentInboxActionRecordMapper actionRecordMapper = mock(AgentInboxActionRecordMapper.class);
        HrReplyRecognitionRecordMapper recognitionMapper = mock(HrReplyRecognitionRecordMapper.class);
        JobApplicationRecordMapper applicationMapper = mock(JobApplicationRecordMapper.class);
        InterviewPrepareRecordMapper prepareMapper = mock(InterviewPrepareRecordMapper.class);
        MockInterviewWrongQuestionMapper wrongQuestionMapper = mock(MockInterviewWrongQuestionMapper.class);
        MockInterviewStudyPlanMapper studyPlanMapper = mock(MockInterviewStudyPlanMapper.class);
        MockInterviewStudyPlanItemMapper studyPlanItemMapper = mock(MockInterviewStudyPlanItemMapper.class);
        JobReminderService reminderService = mock(JobReminderService.class);
        MockInterviewLearningPlanService learningPlanService = mock(MockInterviewLearningPlanService.class);
        MockInterviewWrongQuestionService wrongQuestionService = mock(MockInterviewWrongQuestionService.class);

        HrReplyRecognitionRecord recognition = new HrReplyRecognitionRecord();
        recognition.setId(9L);
        recognition.setUserId(1L);
        recognition.setApplicationId(6L);
        recognition.setCommunicationId(7L);
        recognition.setCompanyName("示例科技");
        recognition.setJobTitle("Java 后端工程师");
        recognition.setIntentType("INTERVIEW_INVITE");
        recognition.setReason("HR 提到了明天下午三点面试");
        recognition.setCreateTime(new Date());

        when(recognitionMapper.selectList(any())).thenReturn(List.of(recognition));
        when(reminderMapper.selectList(any())).thenReturn(List.of());
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(prepareMapper.selectList(any())).thenReturn(List.of());
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of());
        when(studyPlanMapper.selectList(any())).thenReturn(List.of());
        when(studyPlanItemMapper.selectList(any())).thenReturn(List.of());
        when(actionRecordMapper.selectList(any())).thenReturn(List.of());

        AgentInboxServiceImpl service = new AgentInboxServiceImpl(
                actionRecordMapper,
                reminderMapper,
                recognitionMapper,
                applicationMapper,
                prepareMapper,
                wrongQuestionMapper,
                studyPlanMapper,
                studyPlanItemMapper,
                reminderService,
                learningPlanService,
                wrongQuestionService
        );

        AgentInboxVO inbox = service.getTodayInbox(1L);

        assertThat(inbox.getTotalCount()).isEqualTo(1);
        assertThat(inbox.getHighPriorityCount()).isEqualTo(1);
        assertThat(inbox.getItems()).hasSize(1);
        assertThat(inbox.getItems().get(0).getItemType()).isEqualTo("HR_REPLY_CONFIRM");
        assertThat(inbox.getItems().get(0).getPriority()).isEqualTo("HIGH");
        assertThat(inbox.getItems().get(0).getTitle()).contains("示例科技");
        assertThat(inbox.getItems().get(0).getTargetPath()).isEqualTo("/communication");
    }
}
