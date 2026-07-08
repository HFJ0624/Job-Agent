package com.job.bootstrap.service;

import com.job.common.entity.interview.InterviewQuestionBank;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;

import java.util.List;

/**
 * AI 模拟面试题目选择服务。
 *
 * <p>核心职责：根据用户岗位、简历信息及历史答题情况，智能选择匹配度最高的模拟面试题目，支持去重过滤和薄弱知识点优先策略。</p>
 *
 * <p>所属业务模块：面试辅助 / 模拟面试题库引擎</p>
 *
 * <p>主要调用链：MockInterviewService → InterviewQuestionSelectorService → 题库 Mapper / RAG 检索引擎 / 错题本 Service</p>
 */
public interface InterviewQuestionSelectorService {

    /**
     * 默认最近抽题去重窗口，单位小时。
     */
    int DEFAULT_EXCLUDE_RECENT_HOURS = 72;

    /**
     * 根据岗位和简历信息选择本轮模拟面试题目。
     *
     * @param job           目标岗位信息（包含职位名称、技术要求、经验级别等）
     * @param resume        用户简历信息（包含技能栈、工作经历、项目经验等）
     * @param questionCount 本轮需要抽取的题目数量
     * @return 匹配岗位与简历的面试题目列表
     */
    List<InterviewQuestionBank> selectQuestions(JobPosition job, JobResume resume, int questionCount);

    /**
     * 根据岗位和简历选择本轮模拟面试题目，并尽量避开用户最近已抽取过的题目。
     *
     * @param userId             当前用户 ID，用于查询该用户的历史抽题记录
     * @param job                目标岗位信息
     * @param resume             用户简历信息
     * @param questionCount      需要的题目数量
     * @param excludeRecentHours 最近抽题去重窗口，单位小时；0 表示不启用最近题过滤
     * @return 去重后的面试题目列表
     */
    List<InterviewQuestionBank> selectQuestions(
            Long userId,
            JobPosition job,
            JobResume resume,
            int questionCount,
            Integer excludeRecentHours
    );

    /**
     * 根据用户薄弱知识点优先选择题目，提升针对性训练效果。
     *
     * @param userId             当前用户 ID
     * @param job                目标岗位信息
     * @param resume             用户简历信息
     * @param questionCount      需要的题目数量
     * @param excludeRecentHours 最近抽题去重窗口，单位小时；0 表示不启用最近题过滤
     * @param weakKeywords       从错题本提取的未掌握/复习中知识点，排在检索关键词前面以提高相关题优先级
     * @return 优先覆盖薄弱知识点的面试题目列表
     */
    List<InterviewQuestionBank> selectQuestions(
            Long userId,
            JobPosition job,
            JobResume resume,
            int questionCount,
            Integer excludeRecentHours,
            List<String> weakKeywords
    );
}
