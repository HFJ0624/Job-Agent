package com.job.bootstrap.service.dashboard;

import com.job.bootstrap.mapper.AgentObservationAlertRecordMapper;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.mapper.AiModelCallLogMapper;
import com.job.bootstrap.mapper.AiModelConfigMapper;
import com.job.bootstrap.mapper.AiPromptVersionMapper;
import com.job.bootstrap.mapper.JobPositionMapper;
import com.job.bootstrap.mapper.JobResumeMapper;
import com.job.bootstrap.mapper.JobUserMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.mapper.RagChunkMapper;
import com.job.bootstrap.mapper.RagDocumentMapper;
import com.job.bootstrap.service.impl.AdminDashboardServiceImpl;
import com.job.common.vo.admin.AdminDashboardOverviewVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 后台首页看板聚合服务测试。
 *
 * 说明:
 * 1. 看板服务本身不负责复杂业务计算，只负责把多个业务表的统计结果组装成首页可展示结构。
 * 2. 这里用 Mock Mapper 固定数据库返回值，确保测试只关注“组装结果是否正确”。
 */
class AdminDashboardServiceImplTest {

    @Test
    void shouldBuildOverviewWithRealDatabaseCounts() {
        JobUserMapper userMapper = mock(JobUserMapper.class);
        JobPositionMapper positionMapper = mock(JobPositionMapper.class);
        JobResumeMapper resumeMapper = mock(JobResumeMapper.class);
        AgentTraceLogMapper traceLogMapper = mock(AgentTraceLogMapper.class);
        AiModelCallLogMapper modelCallLogMapper = mock(AiModelCallLogMapper.class);
        RagDocumentMapper ragDocumentMapper = mock(RagDocumentMapper.class);
        RagChunkMapper ragChunkMapper = mock(RagChunkMapper.class);
        AiModelConfigMapper modelConfigMapper = mock(AiModelConfigMapper.class);
        AiPromptVersionMapper promptVersionMapper = mock(AiPromptVersionMapper.class);
        MockInterviewSessionMapper mockInterviewSessionMapper = mock(MockInterviewSessionMapper.class);
        AgentObservationAlertRecordMapper alertRecordMapper = mock(AgentObservationAlertRecordMapper.class);

        // 1. 固定各个统计查询的返回值，模拟真实数据库里已经存在的数据。
        when(userMapper.selectCount(any())).thenReturn(10L, 2L);
        when(positionMapper.selectCount(any())).thenReturn(6L, 1L, 1L);
        when(resumeMapper.selectCount(any())).thenReturn(8L, 5L, 1L);
        when(traceLogMapper.selectCount(any())).thenReturn(20L, 4L, 1L);
        when(modelCallLogMapper.selectCount(any())).thenReturn(18L, 2L);
        when(ragDocumentMapper.selectCount(any())).thenReturn(7L);
        when(ragChunkMapper.selectCount(any())).thenReturn(30L);
        when(modelConfigMapper.selectCount(any())).thenReturn(3L);
        when(promptVersionMapper.selectCount(any())).thenReturn(4L);
        when(mockInterviewSessionMapper.selectCount(any())).thenReturn(9L, 2L);
        when(alertRecordMapper.selectCount(any())).thenReturn(2L);

        AdminDashboardServiceImpl service = new AdminDashboardServiceImpl(
                userMapper,
                positionMapper,
                resumeMapper,
                traceLogMapper,
                modelCallLogMapper,
                ragDocumentMapper,
                ragChunkMapper,
                modelConfigMapper,
                promptVersionMapper,
                mockInterviewSessionMapper,
                alertRecordMapper
        );

        // 2. 执行看板聚合，验证返回结构能直接支撑 admin 首页渲染。
        AdminDashboardOverviewVO overview = service.getOverview();

        assertThat(overview.getMetrics()).hasSize(4);
        assertThat(overview.getMetrics().get(0).getLabel()).isEqualTo("注册用户");
        assertThat(overview.getMetrics().get(0).getValue()).isEqualTo(10L);
        assertThat(overview.getMetrics().get(0).getSubText()).isEqualTo("今日新增 2");
        assertThat(overview.getMetrics().get(1).getValue()).isEqualTo(6L);
        assertThat(overview.getMetrics().get(2).getSubText()).isEqualTo("已解析 5");
        assertThat(overview.getMetrics().get(3).getSubText()).isEqualTo("今日调用 4");

        assertThat(overview.getPendingItems()).hasSize(4);
        assertThat(overview.getPendingItems().get(0).getContent()).contains("1 个待发布岗位");
        assertThat(overview.getPendingItems().get(1).getContent()).contains("1 条失败 Agent Trace");

        assertThat(overview.getSystemItems()).hasSize(5);
        assertThat(overview.getSystemItems().get(0).getLabel()).isEqualTo("RAG 知识库");
        assertThat(overview.getSystemItems().get(0).getValue()).contains("7 篇文档");
    }
}
