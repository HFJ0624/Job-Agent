package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.interview.InterviewQuestionBankQueryDTO;
import com.job.common.dto.interview.InterviewQuestionImportDTO;
import com.job.common.vo.interview.InterviewQuestionBankVO;
import com.job.common.vo.interview.InterviewQuestionImportResultVO;
import com.job.common.vo.rag.RagIndexResultVO;

/**
 * AI 模拟面试题库后台服务。
 *
 * <p>核心职责：为管理员提供面试题库的全生命周期管理能力，包括题目导入、查询、状态管理，以及面向 RAG 检索引擎的索引构建与维护。</p>
 *
 * <p>所属业务模块：面试辅助 / 模拟面试题库管理</p>
 *
 * <p>主要调用链：Admin Controller → InterviewQuestionBankService → 题库领域 Service / Mapper / RAG 索引服务</p>
 */
public interface InterviewQuestionBankService {

    /**
     * 从本地 Markdown 文件批量导入面试题库。
     *
     * @param dto 导入参数（包含文件路径、分类映射、标签规则等）
     * @return 导入结果，包含成功数、失败数、失败明细
     */
    InterviewQuestionImportResultVO importLocalMarkdown(InterviewQuestionImportDTO dto);

    /**
     * 分页查询面试题库列表。
     *
     * @param query 查询条件（包含题目内容、分类、难度、状态等过滤条件）
     * @return 面试题目分页结果
     */
    IPage<InterviewQuestionBankVO> pageQuestions(InterviewQuestionBankQueryDTO query);

    /**
     * 查询指定面试题目详情。
     *
     * @param id 题目 ID
     * @return 题目完整详情，包含题干、答案、解析、标签、难度等信息
     */
    InterviewQuestionBankVO getDetail(Long id);

    /**
     * 更新指定面试题目的状态。
     *
     * @param id     题目 ID
     * @param status 目标状态（如启用、禁用、待审核等）
     */
    void updateStatus(Long id, String status);

    /**
     * 为指定面试题目构建 RAG 索引，用于后续智能抽题检索。
     *
     * @param id 题目 ID
     * @return 索引构建结果，包含索引 ID、状态、耗时等信息
     */
    RagIndexResultVO indexQuestion(Long id);

    /**
     * 为全部启用状态的面试题目批量构建 RAG 索引。
     *
     * @return 批量索引构建结果，包含成功数、失败数、总耗时等统计信息
     */
    RagIndexResultVO indexAllActive();
}
