package com.job.bootstrap.service;

/**
 * 作者: hfj
 * 功能: Agent 自动评测服务
 */
public interface AgentEvalService {

    /**
     * 运行单条 Agent 评测用例。
     *
     * @param caseId 用例ID
     * @return 是否通过
     */
    Boolean runCase(Long caseId);

    /**
     * 运行所有启用的 Agent 评测用例。
     *
     * @return 通过数量
     */
    Integer runAllEnabledCases();
}
