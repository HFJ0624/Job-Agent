package com.job.bootstrap.service;

import com.job.common.dto.interview.MockInterviewStudyPlanGenerateDTO;
import com.job.common.dto.interview.MockInterviewStudyPlanItemStatusDTO;
import com.job.common.dto.interview.MockInterviewStudyPlanRetestSubmitDTO;
import com.job.common.vo.interview.MockInterviewLearningPlanVO;
import com.job.common.vo.interview.MockInterviewStudyPlanRetestVO;

/**
 * 模拟面试学习计划服务。
 */
public interface MockInterviewLearningPlanService {

    /**
     * 基于错题本生成学习计划。
     */
    MockInterviewLearningPlanVO generatePlan(Long userId, MockInterviewStudyPlanGenerateDTO dto);

    /**
     * 查询最新学习计划。
     */
    MockInterviewLearningPlanVO getLatestPlan(Long userId);

    /**
     * 修改每日任务完成状态。
     */
    MockInterviewLearningPlanVO updateItemStatus(Long userId, Long itemId, MockInterviewStudyPlanItemStatusDTO dto);

    /**
     * 为某个学习任务发起复测。
     */
    MockInterviewStudyPlanRetestVO startRetest(Long userId, Long itemId);

    /**
     * 提交复测答案并更新错题掌握状态。
     */
    MockInterviewStudyPlanRetestVO submitRetest(Long userId, Long retestId, MockInterviewStudyPlanRetestSubmitDTO dto);
}
