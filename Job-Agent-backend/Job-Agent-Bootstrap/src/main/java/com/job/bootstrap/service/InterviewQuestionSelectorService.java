package com.job.bootstrap.service;

import com.job.common.entity.interview.InterviewQuestionBank;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;

import java.util.List;

/**
 * AI 模拟面试题目选择服务。
 */
public interface InterviewQuestionSelectorService {

    /**
     * 默认最近抽题去重窗口，单位小时。
     */
    int DEFAULT_EXCLUDE_RECENT_HOURS = 72;

    /**
     * 根据岗位和简历选择本轮模拟面试题目。
     */
    List<InterviewQuestionBank> selectQuestions(JobPosition job, JobResume resume, int questionCount);

    /**
     * 根据岗位和简历选择本轮模拟面试题目，并尽量避开用户最近已经抽到过的题。
     *
     * @param userId 当前用户ID，用于查询该用户自己的历史抽题记录
     * @param job 岗位信息
     * @param resume 简历信息
     * @param questionCount 需要的题目数量
     * @param excludeRecentHours 最近抽题去重窗口，单位小时；0 表示不启用最近题过滤
     */
    List<InterviewQuestionBank> selectQuestions(
            Long userId,
            JobPosition job,
            JobResume resume,
            int questionCount,
            Integer excludeRecentHours
    );

    /**
     * 根据用户薄弱知识点优先选择题目。
     *
     * @param weakKeywords 从错题本提取的未掌握/复习中知识点，排在检索关键词前面，提高相关题优先级
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
