package com.job.bootstrap.service;

import com.job.common.dto.interview.MockInterviewStudyPlanGenerateDTO;
import com.job.common.dto.interview.MockInterviewStudyPlanItemStatusDTO;
import com.job.common.dto.interview.MockInterviewStudyPlanRetestSubmitDTO;
import com.job.common.vo.interview.MockInterviewLearningPlanVO;
import com.job.common.vo.interview.MockInterviewStudyPlanRetestVO;

/**
 * 模拟面试学习计划服务。
 *
 * <p>核心职责：基于用户模拟面试错题本智能生成个性化学习计划，支持每日任务管理、完成状态追踪、薄弱知识点复测，帮助用户系统性提升面试能力。</p>
 *
 * <p>所属业务模块：面试辅助 / 模拟面试学习计划</p>
 *
 * <p>主要调用链：Front Controller → MockInterviewLearningPlanService → 学习计划领域 Service / 错题本 Service / 题目选择 Service</p>
 */
public interface MockInterviewLearningPlanService {

    /**
     * 基于错题本为用户生成个性化学习计划。
     *
     * @param userId 当前用户 ID
     * @param dto    计划生成参数（包含生成策略、时间范围、每日任务量等）
     * @return 生成的学习计划详情，包含每日任务列表、知识点分布、预计完成周期
     */
    MockInterviewLearningPlanVO generatePlan(Long userId, MockInterviewStudyPlanGenerateDTO dto);

    /**
     * 查询当前用户最新的学习计划。
     *
     * @param userId 当前用户 ID
     * @return 最新学习计划详情，若无则返回空
     */
    MockInterviewLearningPlanVO getLatestPlan(Long userId);

    /**
     * 修改学习计划中指定每日任务的完成状态。
     *
     * @param userId 当前用户 ID
     * @param itemId 学习计划任务项 ID
     * @param dto    状态变更参数（包含完成状态、实际用时、笔记等）
     * @return 更新后的学习计划详情
     */
    MockInterviewLearningPlanVO updateItemStatus(Long userId, Long itemId, MockInterviewStudyPlanItemStatusDTO dto);

    /**
     * 为指定学习任务发起薄弱知识点复测。
     *
     * @param userId 当前用户 ID
     * @param itemId 学习计划任务项 ID
     * @return 复测会话信息，包含复测题目、限时要求等
     */
    MockInterviewStudyPlanRetestVO startRetest(Long userId, Long itemId);

    /**
     * 提交复测答案并更新对应错题的掌握状态。
     *
     * @param userId   当前用户 ID
     * @param retestId 复测记录 ID
     * @param dto      复测提交参数（包含答案列表、用时等）
     * @return 复测结果，包含得分、正确率、状态更新明细
     */
    MockInterviewStudyPlanRetestVO submitRetest(Long userId, Long retestId, MockInterviewStudyPlanRetestSubmitDTO dto);
}
