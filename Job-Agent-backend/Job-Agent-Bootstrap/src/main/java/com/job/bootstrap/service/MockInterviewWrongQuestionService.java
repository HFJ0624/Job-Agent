package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.interview.MockInterviewWrongQuestionQueryDTO;
import com.job.common.dto.interview.MockInterviewWrongQuestionStatusDTO;
import com.job.common.vo.interview.MockInterviewWrongQuestionVO;

import java.util.List;

/**
 * 模拟面试错题本服务。
 *
 * <p>核心职责：为用户提供模拟面试错题的集中管理能力，支持错题查询、掌握状态更新、薄弱知识点提取，驱动后续学习计划生成和针对性抽题。</p>
 *
 * <p>所属业务模块：面试辅助 / 模拟面试错题本</p>
 *
 * <p>主要调用链：Front Controller → MockInterviewWrongQuestionService → 错题本领域 Service / Mapper / 学习计划 Service</p>
 */
public interface MockInterviewWrongQuestionService {

    /**
     * 分页查询当前用户的模拟面试错题列表。
     *
     * @param userId 当前用户 ID
     * @param query  错题查询条件（包含掌握状态、知识点、时间范围等过滤条件）
     * @return 错题分页结果，包含题目内容、用户答案、标准答案、解析、掌握状态
     */
    IPage<MockInterviewWrongQuestionVO> pageWrongQuestions(Long userId, MockInterviewWrongQuestionQueryDTO query);

    /**
     * 修改指定错题的掌握状态。
     *
     * @param userId 当前用户 ID
     * @param id     错题记录 ID
     * @param dto    状态变更参数（包含新掌握状态、复习笔记等）
     * @return 更新后的错题详情
     */
    MockInterviewWrongQuestionVO updateMasteryStatus(Long userId, Long id, MockInterviewWrongQuestionStatusDTO dto);

    /**
     * 查询当前用户未掌握或复习中的薄弱知识点列表，用于下一轮面试优先抽题。
     *
     * @param userId 当前用户 ID
     * @param limit  查询条数上限
     * @return 薄弱知识点关键词列表，按优先级降序排列
     */
    List<String> listActiveWeakKnowledgePoints(Long userId, int limit);
}
