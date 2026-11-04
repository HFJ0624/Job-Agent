package com.job.bootstrap.agent.resume;

import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.resume.JobResume;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 简历定位规则（P1 统一后的行为锁定）:
 * 1. 显式 resumeId 优先（交给服务校验归属）；
 * 2. 补参沿用上一轮已选简历；
 * 3. 唯一默认简历 > 唯一简历；
 * 4. 多份无法确定 → 返回问题（列出名称），不要求写数据库 ID。
 */
class ResumeSelectionServiceTest {

    private static final Long USER_ID = 100L;

    private final JobResumeService resumeService = mock(JobResumeService.class);
    private final ResumeSelectionService service = new ResumeSelectionService(resumeService);

    private JobResume resume(Long id, String name, int isDefault) {
        JobResume resume = new JobResume();
        resume.setId(id);
        resume.setUserId(USER_ID);
        resume.setResumeName(name);
        resume.setIsDefault(isDefault);
        return resume;
    }

    @Test
    void explicitResumeIdShouldWin() {
        JobResume target = resume(11L, "后端简历", 0);
        when(resumeService.getUserResumeRequired(USER_ID, 11L)).thenReturn(target);

        ResumeSelectionService.Selection selection =
                service.select(USER_ID, Map.of("resumeId", 11L), null, "分析简历");

        assertThat(selection.needsInput()).isFalse();
        assertThat(selection.resume().getId()).isEqualTo(11L);
    }

    @Test
    void previousSelectionShouldBeKeptOnFollowUp() {
        when(resumeService.listUserResumes(USER_ID)).thenReturn(List.of(
                resume(11L, "后端简历", 0), resume(12L, "算法简历", 0)));

        ResumeSelectionService.Selection selection =
                service.select(USER_ID, Map.of(), 12L, "做匹配分析");

        assertThat(selection.resume().getId()).isEqualTo(12L);
    }

    @Test
    void singleDefaultResumeShouldBePicked() {
        when(resumeService.listUserResumes(USER_ID)).thenReturn(List.of(
                resume(11L, "后端简历", 1), resume(12L, "算法简历", 0)));

        assertThat(service.select(USER_ID, Map.of(), null, "分析简历").resume().getId()).isEqualTo(11L);
    }

    @Test
    void ambiguousResumesShouldAskWithNames() {
        when(resumeService.listUserResumes(USER_ID)).thenReturn(List.of(
                resume(11L, "后端简历", 0), resume(12L, "算法简历", 0)));

        ResumeSelectionService.Selection selection =
                service.select(USER_ID, Map.of(), null, "分析简历");

        assertThat(selection.needsInput()).isTrue();
        assertThat(selection.question()).contains("后端简历").contains("算法简历");
        assertThat(selection.missingFields()).contains("resume");
    }

    @Test
    void noResumeShouldAskForUpload() {
        when(resumeService.listUserResumes(USER_ID)).thenReturn(List.of());

        ResumeSelectionService.Selection selection =
                service.select(USER_ID, Map.of(), null, "做匹配分析");

        assertThat(selection.needsInput()).isTrue();
        assertThat(selection.question()).contains("上传");
    }
}
