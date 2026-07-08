package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentEvalCaseQueryDTO;
import com.job.common.dto.agent.AgentEvalCaseSaveDTO;
import com.job.common.dto.agent.AgentEvalCoreTemplateCreateDTO;
import com.job.common.dto.agent.AgentEvalDatasetQueryDTO;
import com.job.common.dto.agent.AgentEvalDatasetSaveDTO;
import com.job.common.dto.agent.AgentEvalQuickFixDTO;
import com.job.common.dto.agent.AgentEvalResultQueryDTO;
import com.job.common.dto.agent.AgentEvalRunQueryDTO;
import com.job.common.vo.agent.AgentEvalCaseVO;
import com.job.common.vo.agent.AgentEvalCaseQualityReportVO;
import com.job.common.vo.agent.AgentEvalCoreTemplateCreateResultVO;
import com.job.common.vo.agent.AgentEvalDatasetVO;
import com.job.common.vo.agent.AgentEvalHealthReportVO;
import com.job.common.vo.agent.AgentEvalResultDiagnosisVO;
import com.job.common.vo.agent.AgentEvalResultVO;
import com.job.common.vo.agent.AgentEvalRunVO;

import java.util.List;

/**
 * Agent Eval 平台服务。
 *
 * <p>核心职责：负责 Agent 自动化评测体系的全生命周期管理，包括数据集维护、评测用例 CRUD、批量运行调度、结果统计分析与质量报告生成。</p>
 *
 * <p>所属业务模块：Agent 评测与质量保障</p>
 *
 * <p>主要调用链：Admin/Eval Controller → AgentEvalService → Eval 领域 Service / Mapper / Agent 执行引擎</p>
 */
public interface AgentEvalService {

    /**
     * 分页查询评测数据集列表。
     *
     * @param query 数据集查询条件（包含名称、状态等过滤条件）
     * @return 评测数据集分页结果
     */
    IPage<AgentEvalDatasetVO> pageDatasets(AgentEvalDatasetQueryDTO query);

    /**
     * 保存或更新评测数据集。
     *
     * @param id 数据集 ID，为空时新增
     * @param request 数据集保存参数（名称、描述、启用状态等）
     * @return 保存后的数据集详情
     */
    AgentEvalDatasetVO saveDataset(Long id, AgentEvalDatasetSaveDTO request);

    /**
     * 删除指定评测数据集。
     *
     * @param id 数据集 ID
     */
    void deleteDataset(Long id);

    /**
     * 分页查询评测用例列表。
     *
     * @param query 用例查询条件（包含数据集 ID、名称、状态等过滤条件）
     * @return 评测用例分页结果
     */
    IPage<AgentEvalCaseVO> pageCases(AgentEvalCaseQueryDTO query);

    /**
     * 保存或更新评测用例。
     *
     * @param id 用例 ID，为空时新增
     * @param request 用例保存参数（输入、期望输出、断言规则等）
     * @return 保存后的用例详情
     */
    AgentEvalCaseVO saveCase(Long id, AgentEvalCaseSaveDTO request);

    /**
     * 批量创建核心链路评测用例模板。
     *
     * @param request 核心模板创建参数（链路类型、覆盖维度等）
     * @return 模板创建结果，包含成功/失败明细
     */
    AgentEvalCoreTemplateCreateResultVO createCoreTemplates(AgentEvalCoreTemplateCreateDTO request);

    /**
     * 删除指定评测用例。
     *
     * @param id 用例 ID
     */
    void deleteCase(Long id);

    /**
     * 运行单个评测用例。
     *
     * @param caseId 用例 ID
     * @return 本次运行记录，包含状态、耗时、通过情况
     */
    AgentEvalRunVO runCase(Long caseId);

    /**
     * 运行指定数据集下的全部启用用例。
     *
     * @param datasetId 数据集 ID
     * @return 本次批量运行记录
     */
    AgentEvalRunVO runDataset(Long datasetId);

    /**
     * 运行全部启用数据集的所有启用用例。
     *
     * @return 本次全量运行记录
     */
    AgentEvalRunVO runAll();

    /**
     * 将某次运行结果设置为基线版本，用于后续回归比对。
     *
     * @param runId 运行记录 ID
     * @return 更新后的运行记录
     */
    AgentEvalRunVO setBaseline(Long runId);

    /**
     * 分页查询运行记录列表。
     *
     * @param query 运行记录查询条件（包含数据集、触发方式、状态等过滤条件）
     * @return 运行记录分页结果
     */
    IPage<AgentEvalRunVO> pageRuns(AgentEvalRunQueryDTO query);

    /**
     * 分页查询评测结果列表。
     *
     * @param query 结果查询条件（包含运行 ID、用例 ID、通过状态等过滤条件）
     * @return 评测结果分页结果
     */
    IPage<AgentEvalResultVO> pageResults(AgentEvalResultQueryDTO query);

    /**
     * 对指定评测结果进行失败诊断分析。
     *
     * @param resultId 评测结果 ID
     * @return 诊断详情，包含失败原因分类、差异对比、修复建议
     */
    AgentEvalResultDiagnosisVO diagnoseResult(Long resultId);

    /**
     * 对指定评测结果应用快速修复。
     *
     * @param resultId 评测结果 ID
     * @param request 快速修复参数（修复策略、期望调整内容等）
     * @return 修复后的用例详情
     */
    AgentEvalCaseVO applyQuickFix(Long resultId, AgentEvalQuickFixDTO request);

    /**
     * 对指定用例应用质量修复。
     *
     * @param caseId 用例 ID
     * @param request 快速修复参数
     * @return 修复后的用例详情
     */
    AgentEvalCaseVO applyCaseQualityFix(Long caseId, AgentEvalQuickFixDTO request);

    /**
     * 检查 Eval 用例配置质量。
     *
     * @param datasetId 数据集 ID，为空时检查全部启用用例
     * @return 用例质量检查报告
     */
    AgentEvalCaseQualityReportVO checkCaseQuality(Long datasetId);

    /**
     * 查询全部启用的评测数据集列表。
     *
     * @return 启用的数据集列表
     */
    List<AgentEvalDatasetVO> listEnabledDatasets();

    /**
     * 构建 Agent 核心链路质量体检报告。
     *
     * @param datasetId 数据集 ID，为空时使用全量最近一次回归批次
     * @return 核心链路质量体检报告
     */
    AgentEvalHealthReportVO buildHealthReport(Long datasetId);

    /**
     * 兼容旧接口：运行所有启用用例并返回通过数量。
     *
     * @return 通过数量
     */
    Integer runAllEnabledCases();
}
