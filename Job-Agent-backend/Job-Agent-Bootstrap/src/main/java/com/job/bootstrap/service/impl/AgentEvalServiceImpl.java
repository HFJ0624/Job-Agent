package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.AgentEvalCaseMapper;
import com.job.bootstrap.mapper.AgentEvalDatasetMapper;
import com.job.bootstrap.mapper.AgentEvalResultMapper;
import com.job.bootstrap.mapper.AgentEvalRunMapper;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.service.AgentChatService;
import com.job.bootstrap.service.AgentEvalService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.common.dto.agent.AgentEvalCaseQueryDTO;
import com.job.common.dto.agent.AgentEvalCaseSaveDTO;
import com.job.common.dto.agent.AgentEvalCoreTemplateCreateDTO;
import com.job.common.dto.agent.AgentEvalDatasetQueryDTO;
import com.job.common.dto.agent.AgentEvalDatasetSaveDTO;
import com.job.common.dto.agent.AgentEvalQuickFixDTO;
import com.job.common.dto.agent.AgentEvalResultQueryDTO;
import com.job.common.dto.agent.AgentEvalRunQueryDTO;
import com.job.common.entity.agent.AgentEvalCase;
import com.job.common.entity.agent.AgentEvalDataset;
import com.job.common.entity.agent.AgentEvalResult;
import com.job.common.entity.agent.AgentEvalRun;
import com.job.common.entity.agent.AgentTraceLog;
import com.job.common.vo.agent.AgentChatVO;
import com.job.common.vo.agent.AgentEvalCaseVO;
import com.job.common.vo.agent.AgentEvalCaseQualityReportVO;
import com.job.common.vo.agent.AgentEvalCoreTemplateCreateResultVO;
import com.job.common.vo.agent.AgentEvalDatasetVO;
import com.job.common.vo.agent.AgentEvalHealthReportVO;
import com.job.common.vo.agent.AgentEvalResultDiagnosisVO;
import com.job.common.vo.agent.AgentEvalResultVO;
import com.job.common.vo.agent.AgentEvalRunVO;
import com.job.common.vo.rag.RagSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作者:hfj
 * 功能:Agent Eval 平台服务实现
 * 日期:2026/6/24
 *
 * 设计说明:
 * 1. 第二版引入 LLM-as-Judge，但仍保留规则分，避免模型裁判失败时整条评测不可用。
 * 2. 工具选择、参数准确率、RAG 命中率仍然使用确定性规则判断，保证核心指标可复现。
 * 3. 每次运行会自动和当前数据集/全量基准批次对比，方便判断 Prompt、模型或工具改动是否退化。
 */
@Service
@RequiredArgsConstructor
public class AgentEvalServiceImpl implements AgentEvalService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int ENABLED = 1;
    private static final String RUN_TYPE_CASE = "CASE";
    private static final String RUN_TYPE_DATASET = "DATASET";
    private static final String RUN_TYPE_ALL = "ALL";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_EVAL_TYPE = "END_TO_END";
    private static final String AI_SCENE_EVAL_JUDGE = "EVAL_JUDGE";
    private static final String QUICK_FIX_COPY_ACTUAL_TOOL = "COPY_ACTUAL_TOOL_TO_EXPECTED";
    private static final String QUICK_FIX_CLEAR_EXPECTED_TOOL = "CLEAR_EXPECTED_TOOL";
    private static final String QUICK_FIX_CLEAR_ANSWER_KEYWORDS = "CLEAR_ANSWER_KEYWORDS";
    private static final String QUICK_FIX_CLEAR_RAG_KEYWORDS = "CLEAR_RAG_KEYWORDS";
    private static final String QUALITY_FIX_SET_MIN_ANSWER_SCORE = "SET_MIN_ANSWER_SCORE_70";
    private static final String QUALITY_FIX_ADD_GUARDRAIL_KEYWORDS = "ADD_GUARDRAIL_REJECT_KEYWORDS";
    private static final String QUALITY_FIX_ADD_JSON_KEYWORDS = "ADD_JSON_FIELD_KEYWORDS";

    private final AgentEvalDatasetMapper agentEvalDatasetMapper;
    private final AgentEvalCaseMapper agentEvalCaseMapper;
    private final AgentEvalRunMapper agentEvalRunMapper;
    private final AgentEvalResultMapper agentEvalResultMapper;
    private final AgentTraceLogMapper agentTraceLogMapper;
    private final AgentChatService agentChatService;
    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;
    private final AgentEvalHealthReportBuilder healthReportBuilder;
    private final AgentEvalCoreTemplateFactory coreTemplateFactory;
    private final AgentEvalResultDiagnosisBuilder resultDiagnosisBuilder;
    private final AgentEvalCaseQualityChecker caseQualityChecker;

    @Override
    public IPage<AgentEvalDatasetVO> pageDatasets(AgentEvalDatasetQueryDTO query) {
        LambdaQueryWrapper<AgentEvalDataset> wrapper = new LambdaQueryWrapper<AgentEvalDataset>()
                .eq(AgentEvalDataset::getIsDeleted, NOT_DELETED)
                .like(StringUtils.hasText(query.getDatasetName()), AgentEvalDataset::getDatasetName, query.getDatasetName())
                .like(StringUtils.hasText(query.getDatasetCode()), AgentEvalDataset::getDatasetCode, query.getDatasetCode())
                .eq(StringUtils.hasText(query.getEvalType()), AgentEvalDataset::getEvalType, query.getEvalType())
                .eq(query.getEnableStatus() != null, AgentEvalDataset::getEnableStatus, query.getEnableStatus())
                .orderByDesc(AgentEvalDataset::getCreateTime);

        return agentEvalDatasetMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper)
                .convert(AgentEvalDatasetVO::from);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalDatasetVO saveDataset(Long id, AgentEvalDatasetSaveDTO request) {
        validateDatasetRequest(request, id);
        Date now = new Date();
        AgentEvalDataset dataset = id == null ? new AgentEvalDataset() : getDatasetRequired(id);
        dataset.setDatasetName(trimToNull(request.getDatasetName()));
        dataset.setDatasetCode(trimToNull(request.getDatasetCode()));
        dataset.setDescription(trimToNull(request.getDescription()));
        dataset.setEvalType(defaultText(request.getEvalType(), DEFAULT_EVAL_TYPE));
        dataset.setEnableStatus(request.getEnableStatus() == null ? ENABLED : request.getEnableStatus());
        dataset.setRemark(trimToNull(request.getRemark()));
        dataset.setUpdateTime(now);

        if (id == null) {
            dataset.setIsDeleted(NOT_DELETED);
            dataset.setCreateTime(now);
            agentEvalDatasetMapper.insert(dataset);
        } else {
            agentEvalDatasetMapper.updateById(dataset);
        }
        return AgentEvalDatasetVO.from(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long id) {
        AgentEvalDataset dataset = getDatasetRequired(id);
        dataset.setIsDeleted(DELETED);
        dataset.setEnableStatus(0);
        dataset.setUpdateTime(new Date());
        agentEvalDatasetMapper.updateById(dataset);
    }

    @Override
    public IPage<AgentEvalCaseVO> pageCases(AgentEvalCaseQueryDTO query) {
        LambdaQueryWrapper<AgentEvalCase> wrapper = new LambdaQueryWrapper<AgentEvalCase>()
                .eq(AgentEvalCase::getIsDeleted, NOT_DELETED)
                .eq(query.getDatasetId() != null, AgentEvalCase::getDatasetId, query.getDatasetId())
                .like(StringUtils.hasText(query.getCaseName()), AgentEvalCase::getCaseName, query.getCaseName())
                .eq(query.getUserId() != null, AgentEvalCase::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getEvalType()), AgentEvalCase::getEvalType, query.getEvalType())
                .like(StringUtils.hasText(query.getExpectedToolName()), AgentEvalCase::getExpectedToolName, query.getExpectedToolName())
                .eq(query.getEnableStatus() != null, AgentEvalCase::getEnableStatus, query.getEnableStatus())
                .orderByDesc(AgentEvalCase::getCreateTime);

        return agentEvalCaseMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper)
                .convert(AgentEvalCaseVO::from);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalCaseVO saveCase(Long id, AgentEvalCaseSaveDTO request) {
        validateCaseRequest(request);
        Date now = new Date();
        AgentEvalCase evalCase = id == null ? new AgentEvalCase() : getCaseRequired(id);
        fillCase(evalCase, request);
        evalCase.setUpdateTime(now);

        if (id == null) {
            evalCase.setIsDeleted(NOT_DELETED);
            evalCase.setCreateTime(now);
            agentEvalCaseMapper.insert(evalCase);
        } else {
            agentEvalCaseMapper.updateById(evalCase);
        }
        return AgentEvalCaseVO.from(evalCase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalCoreTemplateCreateResultVO createCoreTemplates(AgentEvalCoreTemplateCreateDTO request) {
        /*
         * 核心链路模板生成步骤:
         * 1. 校验数据集和测试用户，保证生成的模板有明确归属。
         * 2. 查询当前数据集下已有 CORE_TEMPLATE 用例，避免重复生成。
         * 3. overwrite=true 时先软删旧模板，再插入新模板；overwrite=false 时跳过已有类型。
         * 4. 插入完成后返回创建数量和跳过类型，前端据此给出明确提示。
         */
        validateCoreTemplateRequest(request);
        Long datasetId = request.getDatasetId();
        Long userId = request.getUserId();
        boolean overwrite = Boolean.TRUE.equals(request.getOverwrite());
        List<AgentEvalCase> existingTemplates = listCoreTemplateCases(datasetId);

        AgentEvalCoreTemplateCreateResultVO result = coreTemplateFactory.buildTemplates(datasetId, userId, overwrite, existingTemplates);
        if (overwrite && !existingTemplates.isEmpty()) {
            softDeleteCoreTemplates(existingTemplates);
        }

        Date now = new Date();
        for (AgentEvalCase evalCase : result.getCreatedCases()) {
            evalCase.setIsDeleted(NOT_DELETED);
            evalCase.setCreateTime(now);
            evalCase.setUpdateTime(now);
            agentEvalCaseMapper.insert(evalCase);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long id) {
        AgentEvalCase evalCase = getCaseRequired(id);
        evalCase.setIsDeleted(DELETED);
        evalCase.setEnableStatus(0);
        evalCase.setUpdateTime(new Date());
        agentEvalCaseMapper.updateById(evalCase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalRunVO runCase(Long caseId) {
        AgentEvalCase evalCase = getCaseRequired(caseId);
        AgentEvalRun run = createRun(evalCase.getDatasetId(), RUN_TYPE_CASE, "单条用例回归-" + evalCase.getCaseName(), 1);
        AgentEvalResult result = executeCase(run.getId(), evalCase);
        finishRun(run, List.of(result), null);
        return AgentEvalRunVO.from(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalRunVO runDataset(Long datasetId) {
        AgentEvalDataset dataset = getDatasetRequired(datasetId);
        List<AgentEvalCase> cases = listEnabledCases(datasetId);
        AgentEvalRun run = createRun(datasetId, RUN_TYPE_DATASET, "数据集回归-" + dataset.getDatasetName(), cases.size());
        List<AgentEvalResult> results = executeCases(run.getId(), cases);
        finishRun(run, results, cases.isEmpty() ? "数据集没有启用用例" : null);
        return AgentEvalRunVO.from(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalRunVO runAll() {
        List<AgentEvalCase> cases = listEnabledCases(null);
        AgentEvalRun run = createRun(null, RUN_TYPE_ALL, "全量回归-" + System.currentTimeMillis(), cases.size());
        List<AgentEvalResult> results = executeCases(run.getId(), cases);
        finishRun(run, results, cases.isEmpty() ? "没有启用用例" : null);
        return AgentEvalRunVO.from(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalRunVO setBaseline(Long runId) {
        AgentEvalRun run = getRunRequired(runId);

        /*
         * 同一个数据集或全量范围只保留一个 baseline。
         * datasetId 为空时表示全量基准；datasetId 有值时表示该数据集自己的基准。
         */
        List<AgentEvalRun> oldBaselines = agentEvalRunMapper.selectList(new LambdaQueryWrapper<AgentEvalRun>()
                .eq(AgentEvalRun::getIsDeleted, NOT_DELETED)
                .eq(AgentEvalRun::getBaselineFlag, 1)
                .eq(run.getDatasetId() == null, AgentEvalRun::getRunType, RUN_TYPE_ALL)
                .eq(run.getDatasetId() != null, AgentEvalRun::getDatasetId, run.getDatasetId()));
        Date now = new Date();
        for (AgentEvalRun oldBaseline : oldBaselines) {
            if (!Objects.equals(oldBaseline.getId(), runId)) {
                oldBaseline.setBaselineFlag(0);
                oldBaseline.setUpdateTime(now);
                agentEvalRunMapper.updateById(oldBaseline);
            }
        }

        run.setBaselineFlag(1);
        run.setCompareRunId(null);
        run.setPassRateDelta(null);
        run.setToolAccuracyDelta(null);
        run.setParamAccuracyDelta(null);
        run.setRagHitRateDelta(null);
        run.setAnswerQualityDelta(null);
        run.setUpdateTime(now);
        agentEvalRunMapper.updateById(run);
        return AgentEvalRunVO.from(run);
    }

    @Override
    public IPage<AgentEvalRunVO> pageRuns(AgentEvalRunQueryDTO query) {
        LambdaQueryWrapper<AgentEvalRun> wrapper = new LambdaQueryWrapper<AgentEvalRun>()
                .eq(AgentEvalRun::getIsDeleted, NOT_DELETED)
                .eq(query.getDatasetId() != null, AgentEvalRun::getDatasetId, query.getDatasetId())
                .eq(StringUtils.hasText(query.getRunType()), AgentEvalRun::getRunType, query.getRunType())
                .eq(StringUtils.hasText(query.getStatus()), AgentEvalRun::getStatus, query.getStatus())
                .orderByDesc(AgentEvalRun::getCreateTime);

        return agentEvalRunMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper)
                .convert(AgentEvalRunVO::from);
    }

    @Override
    public IPage<AgentEvalResultVO> pageResults(AgentEvalResultQueryDTO query) {
        LambdaQueryWrapper<AgentEvalResult> wrapper = new LambdaQueryWrapper<AgentEvalResult>()
                .eq(query.getRunId() != null, AgentEvalResult::getRunId, query.getRunId())
                .eq(query.getDatasetId() != null, AgentEvalResult::getDatasetId, query.getDatasetId())
                .eq(query.getCaseId() != null, AgentEvalResult::getCaseId, query.getCaseId())
                .eq(StringUtils.hasText(query.getEvalType()), AgentEvalResult::getEvalType, query.getEvalType())
                .eq(query.getPassStatus() != null, AgentEvalResult::getPassStatus, query.getPassStatus())
                .eq(StringUtils.hasText(query.getFailureType()), AgentEvalResult::getFailureType, query.getFailureType())
                .orderByDesc(AgentEvalResult::getCreateTime);

        return agentEvalResultMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper)
                .convert(AgentEvalResultVO::from);
    }

    @Override
    public AgentEvalResultDiagnosisVO diagnoseResult(Long resultId) {
        /*
         * 单条结果诊断步骤:
         * 1. 先按 ID 查询 Eval 结果，保证诊断基于真实落库数据。
         * 2. 如果结果不存在，直接返回明确异常，避免前端展示空诊断。
         * 3. 诊断规则交给 AgentEvalResultDiagnosisBuilder，Service 不写具体规则。
         */
        AgentEvalResult result = agentEvalResultMapper.selectById(resultId);
        if (result == null) {
            throw new IllegalArgumentException("Eval 结果不存在");
        }
        return resultDiagnosisBuilder.build(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalCaseVO applyQuickFix(Long resultId, AgentEvalQuickFixDTO request) {
        /*
         * 快捷修复步骤:
         * 1. 先通过 Eval 结果找到原始用例，确保修复的是测试用例而不是真实业务数据。
         * 2. 校验 actionType 是否属于白名单，避免前端任意传字段造成越权修改。
         * 3. 根据 actionType 修改用例断言字段，例如清空过严关键词或回填实际工具名。
         * 4. 更新用例后返回最新 VO，前端刷新列表即可看到变化。
         */
        if (request == null || !StringUtils.hasText(request.getActionType())) {
            throw new IllegalArgumentException("快捷修复类型不能为空");
        }
        AgentEvalResult result = getResultRequired(resultId);
        AgentEvalCase evalCase = getCaseRequired(result.getCaseId());
        String actionType = request.getActionType().trim();

        if (QUICK_FIX_COPY_ACTUAL_TOOL.equals(actionType)) {
            evalCase.setExpectedToolName(resolveFirstActualTool(result));
        } else if (QUICK_FIX_CLEAR_EXPECTED_TOOL.equals(actionType)) {
            evalCase.setExpectedToolName(null);
            evalCase.setExpectedToolParamsJson(null);
        } else if (QUICK_FIX_CLEAR_ANSWER_KEYWORDS.equals(actionType)) {
            evalCase.setExpectedAnswerKeywords(null);
        } else if (QUICK_FIX_CLEAR_RAG_KEYWORDS.equals(actionType)) {
            evalCase.setExpectedRagKeywords(null);
            evalCase.setExpectedRagDocumentId(null);
            evalCase.setExpectedRagChunkId(null);
        } else {
            throw new IllegalArgumentException("不支持的快捷修复类型: " + actionType);
        }

        evalCase.setUpdateTime(new Date());
        agentEvalCaseMapper.updateById(evalCase);
        return AgentEvalCaseVO.from(evalCase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvalCaseVO applyCaseQualityFix(Long caseId, AgentEvalQuickFixDTO request) {
        /*
         * 用例质量快捷修复步骤:
         * 1. 只根据 caseId 查询 Eval 用例，不依赖前端传入的字段值，避免越权更新任意列。
         * 2. actionType 必须命中后端白名单；需要人工判断的问题只允许编辑，不提供自动修复。
         * 3. 每种修复只写入确定性默认值，例如最低分 70、拒答关键词、JSON 字段关键词。
         * 4. 更新后返回最新用例，前端刷新质量报告和用例列表即可看到闭环结果。
         */
        if (request == null || !StringUtils.hasText(request.getActionType())) {
            throw new IllegalArgumentException("质量修复类型不能为空");
        }
        AgentEvalCase evalCase = getCaseRequired(caseId);
        String actionType = request.getActionType().trim();

        if (QUALITY_FIX_SET_MIN_ANSWER_SCORE.equals(actionType)) {
            evalCase.setMinAnswerScore(BigDecimal.valueOf(70));
        } else if (QUALITY_FIX_ADD_GUARDRAIL_KEYWORDS.equals(actionType)) {
            evalCase.setExpectedAnswerKeywords(appendKeywords(evalCase.getExpectedAnswerKeywords(), List.of("不能", "无法", "安全", "拒绝", "不可以")));
        } else if (QUALITY_FIX_ADD_JSON_KEYWORDS.equals(actionType)) {
            evalCase.setExpectedAnswerKeywords(appendKeywords(evalCase.getExpectedAnswerKeywords(), List.of("JSON", "title", "summary")));
        } else {
            throw new IllegalArgumentException("不支持的质量快捷修复类型: " + actionType);
        }

        evalCase.setUpdateTime(new Date());
        agentEvalCaseMapper.updateById(evalCase);
        return AgentEvalCaseVO.from(evalCase);
    }

    @Override
    public AgentEvalCaseQualityReportVO checkCaseQuality(Long datasetId) {
        /*
         * 用例质量检查步骤:
         * 1. 复用回归运行已有的启用用例查询逻辑，保证检查范围和实际运行范围一致。
         * 2. 将规则判断交给 AgentEvalCaseQualityChecker，Service 只负责取数和编排，避免规则散落在接口层。
         * 3. 本接口只返回问题报告，不修改用例数据，后续是否修复仍由管理员确认。
         */
        List<AgentEvalCase> cases = listEnabledCases(datasetId);
        return caseQualityChecker.check(cases);
    }

    @Override
    public List<AgentEvalDatasetVO> listEnabledDatasets() {
        return agentEvalDatasetMapper.selectList(new LambdaQueryWrapper<AgentEvalDataset>()
                        .eq(AgentEvalDataset::getEnableStatus, ENABLED)
                        .eq(AgentEvalDataset::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentEvalDataset::getCreateTime))
                .stream()
                .map(AgentEvalDatasetVO::from)
                .toList();
    }

    @Override
    public AgentEvalHealthReportVO buildHealthReport(Long datasetId) {
        /*
         * 体检报告查询步骤:
         * 1. 先按数据集范围查最近一次 Eval 批次，作为本次质量体检的主批次。
         * 2. 再查当前启用用例，用它计算工具、RAG、记忆、Guardrails、JSON 输出这五类核心链路覆盖率。
         * 3. 如果存在最近批次，则查该批次所有结果，用它统计失败分类、薄弱指标和质量建议。
         * 4. 统计逻辑交给 AgentEvalHealthReportBuilder，Service 只负责数据准备，避免接口层堆复杂规则。
         */
        AgentEvalRun latestRun = findLatestRun(datasetId);
        List<AgentEvalCase> enabledCases = listEnabledCases(datasetId);
        List<AgentEvalResult> latestResults = latestRun == null ? Collections.emptyList() : listResultsByRun(latestRun.getId());
        return healthReportBuilder.build(latestRun, enabledCases, latestResults);
    }

    @Override
    public Integer runAllEnabledCases() {
        return runAll().getPassCount();
    }

    private AgentEvalRun findLatestRun(Long datasetId) {
        LambdaQueryWrapper<AgentEvalRun> wrapper = new LambdaQueryWrapper<AgentEvalRun>()
                .eq(AgentEvalRun::getIsDeleted, NOT_DELETED)
                .eq(datasetId != null, AgentEvalRun::getDatasetId, datasetId)
                .eq(datasetId == null, AgentEvalRun::getRunType, RUN_TYPE_ALL)
                .orderByDesc(AgentEvalRun::getCreateTime)
                .last("limit 1");
        return agentEvalRunMapper.selectOne(wrapper);
    }

    private List<AgentEvalResult> listResultsByRun(Long runId) {
        if (runId == null) {
            return Collections.emptyList();
        }
        return agentEvalResultMapper.selectList(new LambdaQueryWrapper<AgentEvalResult>()
                .eq(AgentEvalResult::getRunId, runId)
                .orderByAsc(AgentEvalResult::getId));
    }

    private List<AgentEvalResult> executeCases(Long runId, List<AgentEvalCase> cases) {
        List<AgentEvalResult> results = new ArrayList<>();
        for (AgentEvalCase evalCase : cases) {
            results.add(executeCase(runId, evalCase));
        }
        return results;
    }

    /**
     * 执行单条用例。
     *
     * 方法步骤:
     * 1. 调用真实 AgentChatService，完整走模型、Planner、Executor、工具和 RAG 链路。
     * 2. 从本轮 conversationId 对应的 trace 中提取实际工具、参数和 RAG 召回结果。
     * 3. 按用例配置逐项计算工具选择、参数、RAG 和回答质量。
     * 4. 保存单条结果，即使异常也保存，方便后台定位回归失败原因。
     */
    private AgentEvalResult executeCase(Long runId, AgentEvalCase evalCase) {
        long start = System.currentTimeMillis();
        Date beginTime = new Date();
        AgentChatVO chatVO = null;
        List<AgentTraceLog> traces = Collections.emptyList();
        EvalCheck check = new EvalCheck();

        try {
            chatVO = agentChatService.chat(evalCase.getUserId(), null, null, evalCase.getInputMessage(), List.of());
            traces = listCaseTraces(evalCase, chatVO, beginTime);
            check = evaluateCase(evalCase, chatVO, traces);
        } catch (Exception exception) {
            check.pass = false;
            check.failureType = "EXECUTION_ERROR";
            check.failReasons.add("执行异常：" + exception.getMessage());
        }

        AgentEvalResult result = new AgentEvalResult();
        result.setRunId(runId);
        result.setDatasetId(evalCase.getDatasetId());
        result.setCaseId(evalCase.getId());
        result.setUserId(evalCase.getUserId());
        result.setConversationId(chatVO == null ? null : chatVO.getConversationId());
        result.setInputMessage(evalCase.getInputMessage());
        result.setEvalType(defaultText(evalCase.getEvalType(), DEFAULT_EVAL_TYPE));
        result.setActualAnswer(chatVO == null ? null : chatVO.getAnswer());
        result.setActualTools(toJson(check.actualTools));
        result.setExpectedToolName(evalCase.getExpectedToolName());
        result.setToolSelectPass(check.toolSelectPass);
        result.setExpectedToolParamsJson(evalCase.getExpectedToolParamsJson());
        result.setActualToolParamsJson(check.actualToolParamsJson);
        result.setToolParamPass(check.toolParamPass);
        result.setRagHitPass(check.ragHitPass);
        result.setRagHitRank(check.ragHitRank);
        result.setRagResultsJson(check.ragResultsJson);
        result.setAnswerKeywordPass(check.answerKeywordPass);
        result.setAnswerQualityScore(check.answerQualityScore);
        result.setJudgeScore(check.judgeScore);
        result.setJudgePass(check.judgePass);
        result.setJudgeReason(check.judgeReason);
        result.setJudgeDetailJson(check.judgeDetailJson);
        result.setPassStatus(check.pass ? 1 : 0);
        result.setFailReason(String.join("；", check.failReasons));
        result.setFailureType(check.failureType);
        result.setTraceId(resolveTraceId(traces));
        result.setCostTime(System.currentTimeMillis() - start);
        result.setCreateTime(new Date());
        agentEvalResultMapper.insert(result);
        return result;
    }

    private EvalCheck evaluateCase(AgentEvalCase evalCase, AgentChatVO chatVO, List<AgentTraceLog> traces) {
        EvalCheck check = new EvalCheck();
        check.actualTools = traces.stream()
                .map(AgentTraceLog::getToolName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        check.actualToolParamsJson = resolveActualToolParams(evalCase, traces);

        evaluateToolSelection(evalCase, check);
        evaluateToolParams(evalCase, check);
        evaluateRagHit(evalCase, traces, check);
        evaluateAnswerQuality(evalCase, chatVO, check);
        evaluateWithJudge(evalCase, chatVO, check);

        check.pass = check.failReasons.isEmpty();
        if (!check.pass && !StringUtils.hasText(check.failureType)) {
            check.failureType = "ASSERTION_FAILED";
        }
        return check;
    }

    private void evaluateToolSelection(AgentEvalCase evalCase, EvalCheck check) {
        if (!StringUtils.hasText(evalCase.getExpectedToolName())) {
            check.toolSelectPass = null;
            return;
        }

        boolean matched = check.actualTools.stream()
                .anyMatch(tool -> tool.contains(evalCase.getExpectedToolName()));
        check.toolSelectPass = matched ? 1 : 0;
        if (!matched) {
            check.failureType = "TOOL_SELECT_FAILED";
            check.failReasons.add("期望工具 " + evalCase.getExpectedToolName() + "，实际工具 " + check.actualTools);
        }
    }

    private void evaluateToolParams(AgentEvalCase evalCase, EvalCheck check) {
        if (!StringUtils.hasText(evalCase.getExpectedToolParamsJson())) {
            check.toolParamPass = null;
            return;
        }

        boolean matched = jsonContains(check.actualToolParamsJson, evalCase.getExpectedToolParamsJson());
        check.toolParamPass = matched ? 1 : 0;
        if (!matched) {
            check.failureType = firstFailure(check.failureType, "TOOL_PARAM_FAILED");
            check.failReasons.add("工具参数不匹配，期望包含 " + evalCase.getExpectedToolParamsJson());
        }
    }

    private void evaluateRagHit(AgentEvalCase evalCase, List<AgentTraceLog> traces, EvalCheck check) {
        if (!hasRagExpectation(evalCase)) {
            check.ragHitPass = null;
            return;
        }

        List<RagSearchResultVO> ragResults = extractRagResults(traces);
        check.ragResultsJson = toJson(ragResults);
        check.ragHitRank = findRagHitRank(evalCase, ragResults);
        boolean hit = check.ragHitRank != null;
        check.ragHitPass = hit ? 1 : 0;
        if (!hit) {
            check.failureType = firstFailure(check.failureType, "RAG_HIT_FAILED");
            check.failReasons.add("RAG 未命中期望文档、切片或关键词");
        }
    }

    private void evaluateAnswerQuality(AgentEvalCase evalCase, AgentChatVO chatVO, EvalCheck check) {
        String answer = chatVO == null ? "" : defaultText(chatVO.getAnswer(), "");
        List<String> keywords = splitKeywords(evalCase.getExpectedAnswerKeywords());
        int keywordHitCount = 0;
        for (String keyword : keywords) {
            if (answer.contains(keyword)) {
                keywordHitCount++;
            }
        }

        if (keywords.isEmpty()) {
            check.answerKeywordPass = null;
        } else {
            boolean allKeywordsHit = keywordHitCount == keywords.size();
            check.answerKeywordPass = allKeywordsHit ? 1 : 0;
            if (!allKeywordsHit) {
                check.failureType = firstFailure(check.failureType, "ANSWER_KEYWORD_FAILED");
                check.failReasons.add("回答关键词命中 " + keywordHitCount + "/" + keywords.size());
            }
        }

        // 第一版回答质量使用规则分：关键词命中占 70 分，回答非空占 30 分，避免引入模型裁判的不稳定性。
        BigDecimal keywordScore = keywords.isEmpty()
                ? BigDecimal.valueOf(70)
                : BigDecimal.valueOf(keywordHitCount * 70.0 / keywords.size());
        BigDecimal nonEmptyScore = StringUtils.hasText(answer) ? BigDecimal.valueOf(30) : BigDecimal.ZERO;
        check.answerQualityScore = keywordScore.add(nonEmptyScore).setScale(2, RoundingMode.HALF_UP);

        if (evalCase.getMinAnswerScore() != null
                && check.answerQualityScore.compareTo(evalCase.getMinAnswerScore()) < 0) {
            check.failureType = firstFailure(check.failureType, "ANSWER_QUALITY_FAILED");
            check.failReasons.add("回答质量分 " + check.answerQualityScore + " 低于阈值 " + evalCase.getMinAnswerScore());
        }
    }

    /**
     * 使用 LLM-as-Judge 评估回答质量。
     *
     * 方法步骤:
     * 1. 把用例输入、期望、实际回答、工具和 RAG 结果组装成裁判上下文。
     * 2. 通过统一模型网关的 EVAL_JUDGE 场景调用模型，复用后台模型路由、Prompt、日志、熔断能力。
     * 3. 模型必须返回 JSON；解析失败或模型不可用时保留规则分，不让评测流程中断。
     * 4. Judge 低于用例 minAnswerScore 时判失败；没有阈值时只记录分数和原因。
     */
    private void evaluateWithJudge(AgentEvalCase evalCase, AgentChatVO chatVO, EvalCheck check) {
        String answer = chatVO == null ? "" : defaultText(chatVO.getAnswer(), "");
        if (!StringUtils.hasText(answer)) {
            return;
        }

        try {
            String prompt = buildJudgePrompt(evalCase, answer, check);
            String response = aiModelGatewayService.chat(
                    AI_SCENE_EVAL_JUDGE,
                    buildJudgeVariables(evalCase, answer, check),
                    prompt,
                    evalCase.getUserId(),
                    buildJudgeTraceId(evalCase)
            );
            JudgeResult judgeResult = parseJudgeResult(response);
            check.judgeScore = BigDecimal.valueOf(judgeResult.score()).setScale(2, RoundingMode.HALF_UP);
            check.judgePass = judgeResult.pass() ? 1 : 0;
            check.judgeReason = limitText(judgeResult.reason(), 1000);
            check.judgeDetailJson = toJson(judgeResult);

            if (!judgeResult.pass()) {
                check.failureType = firstFailure(check.failureType, "JUDGE_FAILED");
                check.failReasons.add("Judge 判定失败：" + check.judgeReason);
            }

            if (evalCase.getMinAnswerScore() != null
                    && check.judgeScore.compareTo(evalCase.getMinAnswerScore()) < 0) {
                check.failureType = firstFailure(check.failureType, "JUDGE_SCORE_LOW");
                check.failReasons.add("Judge 分数 " + check.judgeScore + " 低于阈值 " + evalCase.getMinAnswerScore());
            }
        } catch (Exception exception) {
            check.judgePass = null;
            check.judgeReason = "Judge 调用失败：" + limitText(exception.getMessage(), 500);
        }
    }

    private Map<String, Object> buildJudgeVariables(AgentEvalCase evalCase, String answer, EvalCheck check) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("caseName", evalCase.getCaseName());
        variables.put("inputMessage", evalCase.getInputMessage());
        variables.put("expectedIntent", evalCase.getExpectedIntent());
        variables.put("expectedToolName", evalCase.getExpectedToolName());
        variables.put("expectedAnswerKeywords", evalCase.getExpectedAnswerKeywords());
        variables.put("actualAnswer", answer);
        variables.put("actualTools", check.actualTools);
        variables.put("ragResults", check.ragResultsJson);
        variables.put("jsonFormat", "只输出 JSON，不要 Markdown。");
        return variables;
    }

    private String buildJudgePrompt(AgentEvalCase evalCase, String answer, EvalCheck check) {
        return """
                你是 Agent Eval 的严格裁判。请根据评测用例判断实际回答质量，输出 JSON，不要 Markdown。
                评分维度:
                1. accuracyScore: 回答是否准确，0-30。
                2. completenessScore: 回答是否覆盖用户目标和期望关键词，0-25。
                3. groundednessScore: 是否基于工具/RAG结果，是否避免幻觉，0-25。
                4. formatScore: 格式是否清晰、可执行、无多余内容，0-20。
                总分 score = 四项之和，范围 0-100。
                pass: score >= 70 且没有明显幻觉。
                输出字段固定:
                {
                  "score": 0,
                  "pass": true,
                  "reason": "一句话原因",
                  "accuracyScore": 0,
                  "completenessScore": 0,
                  "groundednessScore": 0,
                  "formatScore": 0,
                  "hallucinationRisk": "LOW/MEDIUM/HIGH",
                  "suggestions": ["改进建议"]
                }

                用例名称: %s
                用户输入: %s
                期望意图: %s
                期望工具: %s
                期望答案关键词: %s
                实际工具: %s
                RAG结果: %s
                实际回答: %s
                """.formatted(
                evalCase.getCaseName(),
                evalCase.getInputMessage(),
                defaultText(evalCase.getExpectedIntent(), "未配置"),
                defaultText(evalCase.getExpectedToolName(), "未配置"),
                defaultText(evalCase.getExpectedAnswerKeywords(), "未配置"),
                toJson(check.actualTools),
                defaultText(check.ragResultsJson, "[]"),
                answer
        );
    }

    private String buildJudgeTraceId(AgentEvalCase evalCase) {
        return "eval_judge_" + evalCase.getId() + "_" + System.currentTimeMillis();
    }

    private JudgeResult parseJudgeResult(String response) throws Exception {
        String json = extractJson(response);
        JsonNode root = objectMapper.readTree(json);
        return new JudgeResult(
                root.path("score").asDouble(0D),
                root.path("pass").asBoolean(false),
                root.path("reason").asText(""),
                root.path("accuracyScore").asDouble(0D),
                root.path("completenessScore").asDouble(0D),
                root.path("groundednessScore").asDouble(0D),
                root.path("formatScore").asDouble(0D),
                root.path("hallucinationRisk").asText("UNKNOWN"),
                readStringList(root.path("suggestions"))
        );
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw new IllegalArgumentException("Judge 未返回内容");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Judge 未返回合法 JSON");
        }
        return response.substring(start, end + 1);
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }

    private List<AgentTraceLog> listCaseTraces(AgentEvalCase evalCase, AgentChatVO chatVO, Date beginTime) {
        if (chatVO == null || chatVO.getConversationId() == null) {
            return Collections.emptyList();
        }
        return agentTraceLogMapper.selectList(new LambdaQueryWrapper<AgentTraceLog>()
                .eq(AgentTraceLog::getUserId, evalCase.getUserId())
                .eq(AgentTraceLog::getConversationId, chatVO.getConversationId())
                .ge(AgentTraceLog::getCreateTime, beginTime)
                .orderByAsc(AgentTraceLog::getCreateTime));
    }

    private String resolveActualToolParams(AgentEvalCase evalCase, List<AgentTraceLog> traces) {
        if (!StringUtils.hasText(evalCase.getExpectedToolName())) {
            return traces.stream()
                    .filter(trace -> StringUtils.hasText(trace.getToolName()))
                    .map(AgentTraceLog::getInputData)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);
        }
        return traces.stream()
                .filter(trace -> StringUtils.hasText(trace.getToolName()))
                .filter(trace -> trace.getToolName().contains(evalCase.getExpectedToolName()))
                .map(AgentTraceLog::getInputData)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private List<RagSearchResultVO> extractRagResults(List<AgentTraceLog> traces) {
        List<RagSearchResultVO> results = new ArrayList<>();
        for (AgentTraceLog trace : traces) {
            if (!StringUtils.hasText(trace.getToolName()) || !trace.getToolName().toLowerCase().contains("rag")) {
                continue;
            }
            results.addAll(parseRagResults(trace.getOutputData()));
        }
        return results.stream()
                .sorted(Comparator.comparing(RagSearchResultVO::getReferenceNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<RagSearchResultVO> parseRagResults(String outputData) {
        if (!StringUtils.hasText(outputData)) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(outputData);
            JsonNode arrayNode = root.isArray() ? root : firstArrayNode(root);
            if (arrayNode == null || !arrayNode.isArray()) {
                return Collections.emptyList();
            }
            return objectMapper.convertValue(arrayNode, new TypeReference<List<RagSearchResultVO>>() {});
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private JsonNode firstArrayNode(JsonNode root) {
        for (String field : List.of("data", "results", "result", "items", "output")) {
            JsonNode node = root.path(field);
            if (node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private Integer findRagHitRank(AgentEvalCase evalCase, List<RagSearchResultVO> results) {
        for (int index = 0; index < results.size(); index++) {
            RagSearchResultVO result = results.get(index);
            if (matchesRagExpectation(evalCase, result)) {
                return index + 1;
            }
        }
        return null;
    }

    private boolean matchesRagExpectation(AgentEvalCase evalCase, RagSearchResultVO result) {
        if (evalCase.getExpectedRagDocumentId() != null
                && Objects.equals(evalCase.getExpectedRagDocumentId(), result.getDocumentId())) {
            return true;
        }
        if (evalCase.getExpectedRagChunkId() != null
                && Objects.equals(evalCase.getExpectedRagChunkId(), result.getChunkId())) {
            return true;
        }
        List<String> keywords = splitKeywords(evalCase.getExpectedRagKeywords());
        if (!keywords.isEmpty()) {
            String content = defaultText(result.getContent(), "") + " " + defaultText(result.getTitle(), "");
            return keywords.stream().allMatch(content::contains);
        }
        return false;
    }

    private boolean jsonContains(String actualJson, String expectedJson) {
        if (!StringUtils.hasText(expectedJson)) {
            return true;
        }
        if (!StringUtils.hasText(actualJson)) {
            return false;
        }
        try {
            JsonNode actual = objectMapper.readTree(actualJson);
            JsonNode expected = objectMapper.readTree(expectedJson);
            return containsNode(actual, expected);
        } catch (Exception exception) {
            return actualJson.contains(expectedJson);
        }
    }

    private boolean containsNode(JsonNode actual, JsonNode expected) {
        if (expected == null || expected.isNull()) {
            return true;
        }
        if (expected.isValueNode()) {
            return actual != null && actual.asText().equals(expected.asText());
        }
        if (expected.isObject()) {
            if (actual == null || !actual.isObject()) {
                return false;
            }
            var fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!containsNode(actual.path(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) {
            if (actual == null || !actual.isArray()) {
                return false;
            }
            for (JsonNode expectedItem : expected) {
                boolean matched = false;
                for (JsonNode actualItem : actual) {
                    if (containsNode(actualItem, expectedItem)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private AgentEvalRun createRun(Long datasetId, String runType, String runName, int totalCount) {
        Date now = new Date();
        AgentEvalRun run = new AgentEvalRun();
        run.setDatasetId(datasetId);
        run.setRunType(runType);
        run.setRunName(runName);
        run.setTotalCount(totalCount);
        run.setPassCount(0);
        run.setFailCount(0);
        run.setBaselineFlag(0);
        run.setStatus("RUNNING");
        run.setStartTime(now);
        run.setIsDeleted(NOT_DELETED);
        run.setCreateTime(now);
        run.setUpdateTime(now);
        agentEvalRunMapper.insert(run);
        return run;
    }

    private void finishRun(AgentEvalRun run, List<AgentEvalResult> results, String failReason) {
        int total = results.size();
        int passCount = (int) results.stream().filter(result -> result.getPassStatus() != null && result.getPassStatus() == 1).count();
        run.setTotalCount(total);
        run.setPassCount(passCount);
        run.setFailCount(total - passCount);
        run.setToolAccuracy(calcRate(results, AgentEvalResult::getToolSelectPass));
        run.setParamAccuracy(calcRate(results, AgentEvalResult::getToolParamPass));
        run.setRagHitRate(calcRate(results, AgentEvalResult::getRagHitPass));
        run.setAnswerQualityAvg(calcAverageScore(results));
        run.setAvgCostTime(calcAverageCost(results));
        run.setFailureStatsJson(buildFailureStatsJson(results));
        fillBaselineComparison(run, results);
        run.setStatus(total == passCount && !StringUtils.hasText(failReason) ? STATUS_SUCCESS : STATUS_FAILED);
        run.setFailReason(failReason);
        run.setEndTime(new Date());
        run.setUpdateTime(new Date());
        agentEvalRunMapper.updateById(run);
    }

    private void fillBaselineComparison(AgentEvalRun run, List<AgentEvalResult> results) {
        AgentEvalRun baseline = findBaselineRun(run);
        if (baseline == null || Objects.equals(baseline.getId(), run.getId())) {
            return;
        }

        run.setCompareRunId(baseline.getId());
        run.setPassRateDelta(delta(calcPassRate(run), calcPassRate(baseline)));
        run.setToolAccuracyDelta(delta(run.getToolAccuracy(), baseline.getToolAccuracy()));
        run.setParamAccuracyDelta(delta(run.getParamAccuracy(), baseline.getParamAccuracy()));
        run.setRagHitRateDelta(delta(run.getRagHitRate(), baseline.getRagHitRate()));
        run.setAnswerQualityDelta(delta(run.getAnswerQualityAvg(), baseline.getAnswerQualityAvg()));

        Map<Long, AgentEvalResult> baselineResultMap = agentEvalResultMapper.selectList(new LambdaQueryWrapper<AgentEvalResult>()
                        .eq(AgentEvalResult::getRunId, baseline.getId()))
                .stream()
                .collect(Collectors.toMap(AgentEvalResult::getCaseId, Function.identity(), (left, right) -> left));

        for (AgentEvalResult result : results) {
            AgentEvalResult baselineResult = baselineResultMap.get(result.getCaseId());
            if (baselineResult == null) {
                continue;
            }
            result.setBaselineResultId(baselineResult.getId());
            result.setAnswerScoreDelta(delta(result.getAnswerQualityScore(), baselineResult.getAnswerQualityScore()));
            agentEvalResultMapper.updateById(result);
        }
    }

    private AgentEvalRun findBaselineRun(AgentEvalRun run) {
        LambdaQueryWrapper<AgentEvalRun> wrapper = new LambdaQueryWrapper<AgentEvalRun>()
                .eq(AgentEvalRun::getBaselineFlag, 1)
                .eq(AgentEvalRun::getIsDeleted, NOT_DELETED)
                .ne(AgentEvalRun::getId, run.getId())
                .orderByDesc(AgentEvalRun::getCreateTime)
                .last("limit 1");

        if (run.getDatasetId() == null) {
            wrapper.eq(AgentEvalRun::getRunType, RUN_TYPE_ALL);
        } else {
            wrapper.eq(AgentEvalRun::getDatasetId, run.getDatasetId());
        }
        return agentEvalRunMapper.selectOne(wrapper);
    }

    private BigDecimal calcPassRate(AgentEvalRun run) {
        if (run.getTotalCount() == null || run.getTotalCount() == 0) {
            return null;
        }
        return BigDecimal.valueOf(run.getPassCount() * 100.0 / run.getTotalCount()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal delta(BigDecimal current, BigDecimal baseline) {
        if (current == null || baseline == null) {
            return null;
        }
        return current.subtract(baseline).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildFailureStatsJson(List<AgentEvalResult> results) {
        Map<String, Long> stats = results.stream()
                .filter(result -> StringUtils.hasText(result.getFailureType()))
                .collect(Collectors.groupingBy(AgentEvalResult::getFailureType, LinkedHashMap::new, Collectors.counting()));
        return toJson(stats);
    }

    private BigDecimal calcRate(List<AgentEvalResult> results, Function<AgentEvalResult, Integer> getter) {
        List<Integer> values = results.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        long pass = values.stream().filter(value -> value == 1).count();
        return BigDecimal.valueOf(pass * 100.0 / values.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcAverageScore(List<AgentEvalResult> results) {
        List<BigDecimal> scores = results.stream()
                .map(AgentEvalResult::getAnswerQualityScore)
                .filter(Objects::nonNull)
                .toList();
        if (scores.isEmpty()) {
            return null;
        }
        BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private Long calcAverageCost(List<AgentEvalResult> results) {
        return Math.round(results.stream()
                .map(AgentEvalResult::getCostTime)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0D));
    }

    private List<AgentEvalCase> listEnabledCases(Long datasetId) {
        return agentEvalCaseMapper.selectList(new LambdaQueryWrapper<AgentEvalCase>()
                .eq(AgentEvalCase::getEnableStatus, ENABLED)
                .eq(AgentEvalCase::getIsDeleted, NOT_DELETED)
                .eq(datasetId != null, AgentEvalCase::getDatasetId, datasetId)
                .orderByAsc(AgentEvalCase::getId));
    }

    private void validateDatasetRequest(AgentEvalDatasetSaveDTO request, Long excludeId) {
        if (!StringUtils.hasText(request.getDatasetName())) {
            throw new IllegalArgumentException("数据集名称不能为空");
        }
        if (!StringUtils.hasText(request.getDatasetCode())) {
            throw new IllegalArgumentException("数据集编码不能为空");
        }
        Long count = agentEvalDatasetMapper.selectCount(new LambdaQueryWrapper<AgentEvalDataset>()
                .eq(AgentEvalDataset::getDatasetCode, request.getDatasetCode().trim())
                .eq(AgentEvalDataset::getIsDeleted, NOT_DELETED)
                .ne(excludeId != null, AgentEvalDataset::getId, excludeId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("数据集编码已存在");
        }
    }

    private void validateCaseRequest(AgentEvalCaseSaveDTO request) {
        if (!StringUtils.hasText(request.getCaseName())) {
            throw new IllegalArgumentException("用例名称不能为空");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("测试用户ID不能为空");
        }
        if (!StringUtils.hasText(request.getInputMessage())) {
            throw new IllegalArgumentException("用户输入不能为空");
        }
    }

    private void validateCoreTemplateRequest(AgentEvalCoreTemplateCreateDTO request) {
        if (request == null || request.getDatasetId() == null) {
            throw new IllegalArgumentException("请选择要生成模板的数据集");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("测试用户ID不能为空");
        }
        getDatasetRequired(request.getDatasetId());
    }

    private List<AgentEvalCase> listCoreTemplateCases(Long datasetId) {
        return agentEvalCaseMapper.selectList(new LambdaQueryWrapper<AgentEvalCase>()
                .eq(AgentEvalCase::getDatasetId, datasetId)
                .eq(AgentEvalCase::getIsDeleted, NOT_DELETED)
                .like(AgentEvalCase::getTags, "CORE_TEMPLATE")
                .orderByAsc(AgentEvalCase::getId));
    }

    private void softDeleteCoreTemplates(List<AgentEvalCase> existingTemplates) {
        Date now = new Date();
        for (AgentEvalCase evalCase : existingTemplates) {
            evalCase.setIsDeleted(DELETED);
            evalCase.setEnableStatus(0);
            evalCase.setUpdateTime(now);
            agentEvalCaseMapper.updateById(evalCase);
        }
    }

    private void fillCase(AgentEvalCase evalCase, AgentEvalCaseSaveDTO request) {
        evalCase.setDatasetId(request.getDatasetId());
        evalCase.setCaseName(trimToNull(request.getCaseName()));
        evalCase.setUserId(request.getUserId());
        evalCase.setInputMessage(trimToNull(request.getInputMessage()));
        evalCase.setEvalType(defaultText(request.getEvalType(), DEFAULT_EVAL_TYPE));
        evalCase.setExpectedIntent(trimToNull(request.getExpectedIntent()));
        evalCase.setExpectedToolName(trimToNull(request.getExpectedToolName()));
        evalCase.setExpectedToolParamsJson(trimToNull(request.getExpectedToolParamsJson()));
        evalCase.setExpectedRagDocumentId(request.getExpectedRagDocumentId());
        evalCase.setExpectedRagChunkId(request.getExpectedRagChunkId());
        evalCase.setExpectedRagKeywords(trimToNull(request.getExpectedRagKeywords()));
        evalCase.setExpectedAnswerKeywords(trimToNull(request.getExpectedAnswerKeywords()));
        evalCase.setMinAnswerScore(request.getMinAnswerScore());
        evalCase.setTags(trimToNull(request.getTags()));
        evalCase.setEnableStatus(request.getEnableStatus() == null ? ENABLED : request.getEnableStatus());
        evalCase.setRemark(trimToNull(request.getRemark()));
    }

    private AgentEvalDataset getDatasetRequired(Long id) {
        AgentEvalDataset dataset = agentEvalDatasetMapper.selectOne(new LambdaQueryWrapper<AgentEvalDataset>()
                .eq(AgentEvalDataset::getId, id)
                .eq(AgentEvalDataset::getIsDeleted, NOT_DELETED)
                .last("limit 1"));
        if (dataset == null) {
            throw new IllegalArgumentException("Eval 数据集不存在");
        }
        return dataset;
    }

    private AgentEvalCase getCaseRequired(Long id) {
        AgentEvalCase evalCase = agentEvalCaseMapper.selectOne(new LambdaQueryWrapper<AgentEvalCase>()
                .eq(AgentEvalCase::getId, id)
                .eq(AgentEvalCase::getIsDeleted, NOT_DELETED)
                .last("limit 1"));
        if (evalCase == null) {
            throw new IllegalArgumentException("Eval 用例不存在");
        }
        return evalCase;
    }

    private AgentEvalResult getResultRequired(Long id) {
        AgentEvalResult result = agentEvalResultMapper.selectById(id);
        if (result == null) {
            throw new IllegalArgumentException("Eval 结果不存在");
        }
        return result;
    }

    private String resolveFirstActualTool(AgentEvalResult result) {
        if (!StringUtils.hasText(result.getActualTools())) {
            throw new IllegalArgumentException("该结果没有实际工具调用，无法复制为期望工具");
        }
        try {
            JsonNode root = objectMapper.readTree(result.getActualTools());
            if (root.isArray()) {
                for (JsonNode item : root) {
                    if (item.isTextual() && StringUtils.hasText(item.asText())) {
                        return item.asText();
                    }
                }
            }
        } catch (Exception ignored) {
            // actualTools 历史上可能不是标准 JSON 数组，此时退回原文本作为工具名。
        }
        String text = result.getActualTools().trim();
        if (!StringUtils.hasText(text) || "[]".equals(text)) {
            throw new IllegalArgumentException("该结果没有实际工具调用，无法复制为期望工具");
        }
        return text;
    }

    private AgentEvalRun getRunRequired(Long id) {
        AgentEvalRun run = agentEvalRunMapper.selectOne(new LambdaQueryWrapper<AgentEvalRun>()
                .eq(AgentEvalRun::getId, id)
                .eq(AgentEvalRun::getIsDeleted, NOT_DELETED)
                .last("limit 1"));
        if (run == null) {
            throw new IllegalArgumentException("Eval 运行批次不存在");
        }
        return run;
    }

    private boolean hasRagExpectation(AgentEvalCase evalCase) {
        return evalCase.getExpectedRagDocumentId() != null
                || evalCase.getExpectedRagChunkId() != null
                || StringUtils.hasText(evalCase.getExpectedRagKeywords());
    }

    private List<String> splitKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String item : text.split("[,，]")) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private String appendKeywords(String oldKeywords, List<String> keywordsToAdd) {
        /*
         * 关键词合并步骤:
         * 1. 先把原有关键词按逗号、中文逗号、分号拆开，保留管理员原来配置的断言。
         * 2. 使用 LinkedHashSet 去重并保留顺序，避免重复点击快捷修复后产生重复关键词。
         * 3. 最后统一用英文逗号落库，便于后续 splitKeywords 继续解析。
         */
        LinkedHashSet<String> keywords = new LinkedHashSet<>(splitKeywords(oldKeywords));
        for (String keyword : keywordsToAdd) {
            if (StringUtils.hasText(keyword)) {
                keywords.add(keyword.trim());
            }
        }
        return String.join(",", keywords);
    }

    private String resolveTraceId(List<AgentTraceLog> traces) {
        return traces.stream()
                .map(AgentTraceLog::getTraceId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String firstFailure(String oldFailure, String newFailure) {
        return StringUtils.hasText(oldFailure) ? oldFailure : newFailure;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record JudgeResult(
            double score,
            boolean pass,
            String reason,
            double accuracyScore,
            double completenessScore,
            double groundednessScore,
            double formatScore,
            String hallucinationRisk,
            List<String> suggestions
    ) {
    }

    private static class EvalCheck {
        private boolean pass = true;
        private List<String> actualTools = Collections.emptyList();
        private String actualToolParamsJson;
        private Integer toolSelectPass;
        private Integer toolParamPass;
        private Integer ragHitPass;
        private Integer ragHitRank;
        private String ragResultsJson;
        private Integer answerKeywordPass;
        private BigDecimal answerQualityScore;
        private BigDecimal judgeScore;
        private Integer judgePass;
        private String judgeReason;
        private String judgeDetailJson;
        private String failureType;
        private final List<String> failReasons = new ArrayList<>();
    }
}
