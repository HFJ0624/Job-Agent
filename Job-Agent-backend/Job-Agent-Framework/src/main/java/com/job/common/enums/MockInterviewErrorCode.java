package com.job.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 模拟面试细粒度错误码。
 *
 * 说明:
 * 1. 这些错误码用于前端失败状态可视化，不替代 Result.code。
 * 2. 错误码保持稳定，前端不要再通过中文 message 判断失败类型。
 * 3. 后续 admin 诊断、Trace 分类、Eval 回归也可以复用这组错误码。
 */
@Getter
@RequiredArgsConstructor
public enum MockInterviewErrorCode {

    SESSION_NOT_FOUND("MOCK_INTERVIEW_SESSION_NOT_FOUND", "模拟面试会话不存在或无权限访问"),
    JOB_NOT_AVAILABLE("MOCK_INTERVIEW_JOB_NOT_AVAILABLE", "岗位不可用于 AI 面试"),
    QUESTION_NOT_AVAILABLE("MOCK_INTERVIEW_QUESTION_NOT_AVAILABLE", "面试题不可用"),
    QUESTION_ALREADY_ANSWERED("MOCK_INTERVIEW_QUESTION_ALREADY_ANSWERED", "该题已经回答过"),
    NO_AVAILABLE_QUESTION("MOCK_INTERVIEW_NO_AVAILABLE_QUESTION", "没有可用的面试题"),
    ASR_FAILED("MOCK_INTERVIEW_ASR_FAILED", "语音识别失败"),
    AUDIO_SUBMIT_FAILED("MOCK_INTERVIEW_AUDIO_SUBMIT_FAILED", "语音回答提交失败"),
    REVIEW_NO_ANSWER("MOCK_INTERVIEW_REVIEW_NO_ANSWER", "当前面试没有回答记录"),
    REVIEW_JSON_PARSE_FAILED("MOCK_INTERVIEW_REVIEW_JSON_PARSE_FAILED", "AI 复盘结果解析失败"),
    REVIEW_GENERATE_FAILED("MOCK_INTERVIEW_REVIEW_GENERATE_FAILED", "AI 复盘生成失败"),
    STUDY_PLAN_REVIEW_REQUIRED("MOCK_INTERVIEW_STUDY_PLAN_REVIEW_REQUIRED", "请先生成 AI 面试总结"),
    STUDY_MATERIAL_FAILED("MOCK_INTERVIEW_STUDY_MATERIAL_FAILED", "补课材料加载失败");

    private final String code;
    private final String defaultMessage;
}
