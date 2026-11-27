package com.job.bootstrap.application.agent.service;

import com.job.common.vo.agent.EvalBatchReportVO;
import com.job.common.vo.agent.EvalOverviewVO;

/**
 * 功能: 评测"批次"聚合读模型（概览 / 单批次报告）。
 *
 * <p>批次直接复用 agent_eval_run（该表本身就是一次全量运行的聚合记录），
 * 不新增表；本服务只负责把 run + result 组装成前端可直接渲染的结构。</p>
 */
public interface EvalBatchReportService {

    /**
     * 概览：最近批次指标、通过率趋势、失败 Top。
     *
     * @param datasetId 数据集 ID，可为空（全部数据集）
     */
    EvalOverviewVO overview(Long datasetId);

    /**
     * 单批次报告。
     *
     * @param runId 批次（run）ID
     */
    EvalBatchReportVO report(Long runId);
}
