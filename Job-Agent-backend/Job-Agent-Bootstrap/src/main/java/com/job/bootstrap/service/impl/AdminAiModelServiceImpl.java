package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.AiModelCallLogMapper;
import com.job.bootstrap.mapper.AiModelConfigMapper;
import com.job.bootstrap.mapper.AiModelRouteMapper;
import com.job.bootstrap.service.AdminAiModelService;
import com.job.common.dto.ai.AiModelCallLogQueryDTO;
import com.job.common.dto.ai.AiModelConfigQueryDTO;
import com.job.common.dto.ai.AiModelConfigSaveDTO;
import com.job.common.dto.ai.AiModelRouteQueryDTO;
import com.job.common.dto.ai.AiModelRouteSaveDTO;
import com.job.common.entity.ai.AiModelCallLog;
import com.job.common.entity.ai.AiModelConfig;
import com.job.common.entity.ai.AiModelRoute;
import com.job.common.vo.ai.AiModelCallLogVO;
import com.job.common.vo.ai.AiModelConfigVO;
import com.job.common.vo.ai.AiModelCostStatsVO;
import com.job.common.vo.ai.AiModelRouteVO;
import com.job.enums.AiConfigStatus;
import com.job.enums.AiModelCallStatus;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * 作者:hfj
 * 功能:后台 AI 模型与路由管理服务实现
 * 日期:2026/6/21
 */
@Service
@RequiredArgsConstructor
public class AdminAiModelServiceImpl implements AdminAiModelService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final String DEFAULT_CHAT_PATH = "/chat/completions";

    private final AiModelConfigMapper aiModelConfigMapper;
    private final AiModelRouteMapper aiModelRouteMapper;
    private final AiModelCallLogMapper aiModelCallLogMapper;

    /**
     * 分页查询模型配置。
     *
     * @param query 查询条件
     * @return 模型配置分页
     */
    @Override
    public IPage<AiModelConfigVO> pageModels(AiModelConfigQueryDTO query) {
        Page<AiModelConfig> page = new Page<>(safePageNum(query.getPageNum()), safePageSize(query.getPageSize()));
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(query.getModelCode())) {
            wrapper.like(AiModelConfig::getModelCode, query.getModelCode().trim());
        }
        if (StringUtils.hasText(query.getModelName())) {
            wrapper.like(AiModelConfig::getModelName, query.getModelName().trim());
        }
        if (StringUtils.hasText(query.getProvider())) {
            wrapper.eq(AiModelConfig::getProvider, query.getProvider().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AiModelConfig::getStatus, query.getStatus().trim());
        }

        wrapper.orderByDesc(AiModelConfig::getCreateTime);
        return aiModelConfigMapper.selectPage(page, wrapper).convert(AiModelConfigVO::from);
    }

    /**
     * 查询启用模型列表。
     *
     * @return 启用模型
     */
    @Override
    public List<AiModelConfigVO> listActiveModels() {
        return aiModelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getStatus, AiConfigStatus.ACTIVE.name())
                        .eq(AiModelConfig::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AiModelConfig::getCreateTime))
                .stream()
                .map(AiModelConfigVO::from)
                .toList();
    }

    /**
     * 新增模型配置。
     *
     * @param request 模型表单
     * @return 保存后的模型配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigVO createModel(AiModelConfigSaveDTO request) {
        String modelCode = requireText(request.getModelCode(), "模型编码不能为空");
        if (existsModelCode(modelCode, null)) {
            throw new BizException("模型编码已经存在");
        }

        Date now = new Date();
        AiModelConfig model = new AiModelConfig();
        fillModel(model, request, null);
        model.setIsDeleted(NOT_DELETED);
        model.setCreateTime(now);
        model.setUpdateTime(now);
        aiModelConfigMapper.insert(model);
        return AiModelConfigVO.from(model);
    }

    /**
     * 修改模型配置。
     *
     * @param id 模型配置 ID
     * @param request 模型表单
     * @return 修改后的模型配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigVO updateModel(Long id, AiModelConfigSaveDTO request) {
        AiModelConfig model = getModelRequired(id);
        String modelCode = requireText(request.getModelCode(), "模型编码不能为空");
        if (existsModelCode(modelCode, id)) {
            throw new BizException("模型编码已经存在");
        }

        fillModel(model, request, model.getApiKey());
        model.setUpdateTime(new Date());
        aiModelConfigMapper.updateById(model);
        return AiModelConfigVO.from(getModelRequired(id));
    }

    /**
     * 逻辑删除模型配置。
     *
     * @param id 模型配置 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long id) {
        AiModelConfig model = getModelRequired(id);
        model.setIsDeleted(DELETED);
        model.setStatus(AiConfigStatus.DISABLED.name());
        model.setUpdateTime(new Date());
        aiModelConfigMapper.updateById(model);
    }

    /**
     * 分页查询模型路由。
     *
     * @param query 查询条件
     * @return 路由分页
     */
    @Override
    public IPage<AiModelRouteVO> pageRoutes(AiModelRouteQueryDTO query) {
        Page<AiModelRoute> page = new Page<>(safePageNum(query.getPageNum()), safePageSize(query.getPageSize()));
        LambdaQueryWrapper<AiModelRoute> wrapper = new LambdaQueryWrapper<AiModelRoute>()
                .eq(AiModelRoute::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(query.getSceneCode())) {
            wrapper.like(AiModelRoute::getSceneCode, query.getSceneCode().trim());
        }
        if (StringUtils.hasText(query.getPromptCode())) {
            wrapper.like(AiModelRoute::getPromptCode, query.getPromptCode().trim());
        }
        if (StringUtils.hasText(query.getPrimaryModelCode())) {
            wrapper.eq(AiModelRoute::getPrimaryModelCode, query.getPrimaryModelCode().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AiModelRoute::getStatus, query.getStatus().trim());
        }

        wrapper.orderByDesc(AiModelRoute::getCreateTime);
        return aiModelRouteMapper.selectPage(page, wrapper).convert(AiModelRouteVO::from);
    }

    /**
     * 新增模型路由。
     *
     * @param request 路由表单
     * @return 保存后的路由
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelRouteVO createRoute(AiModelRouteSaveDTO request) {
        validateRouteModels(request);

        Date now = new Date();
        AiModelRoute route = new AiModelRoute();
        fillRoute(route, request);
        route.setIsDeleted(NOT_DELETED);
        route.setCreateTime(now);
        route.setUpdateTime(now);
        aiModelRouteMapper.insert(route);
        return AiModelRouteVO.from(route);
    }

    /**
     * 修改模型路由。
     *
     * @param id 路由 ID
     * @param request 路由表单
     * @return 修改后的路由
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelRouteVO updateRoute(Long id, AiModelRouteSaveDTO request) {
        AiModelRoute route = getRouteRequired(id);
        validateRouteModels(request);

        fillRoute(route, request);
        route.setUpdateTime(new Date());
        aiModelRouteMapper.updateById(route);
        return AiModelRouteVO.from(getRouteRequired(id));
    }

    /**
     * 逻辑删除模型路由。
     *
     * @param id 路由 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoute(Long id) {
        AiModelRoute route = getRouteRequired(id);
        route.setIsDeleted(DELETED);
        route.setStatus(AiConfigStatus.DISABLED.name());
        route.setUpdateTime(new Date());
        aiModelRouteMapper.updateById(route);
    }

    /**
     * 分页查询模型调用日志。
     *
     * @param query 查询条件
     * @return 调用日志分页
     */
    @Override
    public IPage<AiModelCallLogVO> pageCallLogs(AiModelCallLogQueryDTO query) {
        Page<AiModelCallLog> page = new Page<>(safePageNum(query.getPageNum()), safePageSize(query.getPageSize()));
        LambdaQueryWrapper<AiModelCallLog> wrapper = buildCallLogWrapper(query);
        wrapper.orderByDesc(AiModelCallLog::getCreateTime);
        return aiModelCallLogMapper.selectPage(page, wrapper).convert(AiModelCallLogVO::from);
    }

    /**
     * 统计模型 token 和成本。
     *
     * 方法步骤:
     * 1. 复用调用日志查询条件，保证列表和统计口径一致。
     * 2. 第一版直接在 Java 内存聚合，避免为了小规模后台统计写复杂 SQL。
     * 3. 后续日志量大时，再把这里替换成数据库 group by 或报表表。
     *
     * @param query 查询条件
     * @return 成本统计
     */
    @Override
    public AiModelCostStatsVO costStats(AiModelCallLogQueryDTO query) {
        List<AiModelCallLog> logs = aiModelCallLogMapper.selectList(buildCallLogWrapper(query));
        AiModelCostStatsVO stats = new AiModelCostStatsVO();

        long costTimeSum = 0L;
        for (AiModelCallLog log : logs) {
            stats.setTotalCalls(stats.getTotalCalls() + 1);
            if (AiModelCallStatus.SUCCESS.name().equals(log.getStatus())) {
                stats.setSuccessCalls(stats.getSuccessCalls() + 1);
            } else {
                stats.setFailedCalls(stats.getFailedCalls() + 1);
            }
            stats.setTotalTokens(stats.getTotalTokens() + nullToZero(log.getTotalTokens()));
            stats.setTotalCost(stats.getTotalCost().add(nullToZero(log.getTotalCost())));
            costTimeSum += nullToZero(log.getCostTime());
        }

        if (!logs.isEmpty()) {
            stats.setAvgCostTime(costTimeSum / logs.size());
        }
        stats.setTotalCost(stats.getTotalCost().setScale(6, RoundingMode.HALF_UP));
        return stats;
    }

    /**
     * 填充模型配置字段。
     *
     * @param model 模型实体
     * @param request 表单参数
     * @param oldApiKey 数据库旧 API Key，编辑时用于保留脱敏值
     */
    private void fillModel(AiModelConfig model, AiModelConfigSaveDTO request, String oldApiKey) {
        model.setModelCode(requireText(request.getModelCode(), "模型编码不能为空"));
        model.setModelName(requireText(request.getModelName(), "模型名称不能为空"));
        model.setProvider(requireText(request.getProvider(), "供应商不能为空"));
        model.setBaseUrl(requireText(request.getBaseUrl(), "BaseUrl 不能为空"));
        model.setApiKey(resolveApiKey(request.getApiKey(), oldApiKey));
        model.setChatPath(StringUtils.hasText(request.getChatPath()) ? request.getChatPath().trim() : DEFAULT_CHAT_PATH);
        model.setModelIdentifier(requireText(request.getModelIdentifier(), "模型标识不能为空"));
        model.setTemperature(request.getTemperature() == null ? new BigDecimal("0.2") : request.getTemperature());
        model.setMaxTokens(request.getMaxTokens() == null ? 1200 : Math.max(1, request.getMaxTokens()));
        model.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 45 : Math.max(1, request.getTimeoutSeconds()));
        model.setMaxRetries(request.getMaxRetries() == null ? 0 : Math.max(0, request.getMaxRetries()));
        model.setInputPricePer1k(request.getInputPricePer1k() == null ? BigDecimal.ZERO : request.getInputPricePer1k());
        model.setOutputPricePer1k(request.getOutputPricePer1k() == null ? BigDecimal.ZERO : request.getOutputPricePer1k());
        model.setCircuitEnabled(request.getCircuitEnabled() == null ? 1 : normalizeBinary(request.getCircuitEnabled()));
        model.setFailureThreshold(request.getFailureThreshold() == null ? 3 : Math.max(1, request.getFailureThreshold()));
        model.setCooldownSeconds(request.getCooldownSeconds() == null ? 60 : Math.max(1, request.getCooldownSeconds()));
        model.setStatus(normalizeConfigStatus(request.getStatus()));
        model.setRemark(trimToNull(request.getRemark()));
    }

    /**
     * 填充路由字段。
     *
     * @param route 路由实体
     * @param request 表单参数
     */
    private void fillRoute(AiModelRoute route, AiModelRouteSaveDTO request) {
        route.setSceneCode(requireText(request.getSceneCode(), "业务场景不能为空"));
        route.setRouteName(requireText(request.getRouteName(), "路由名称不能为空"));
        route.setPrimaryModelCode(requireText(request.getPrimaryModelCode(), "主模型不能为空"));
        route.setFallbackModelCode(trimToNull(request.getFallbackModelCode()));
        route.setPromptCode(requireText(request.getPromptCode(), "Prompt 编码不能为空"));
        route.setPromptVersionId(request.getPromptVersionId());
        route.setGrayPercent(normalizePercent(request.getGrayPercent()));
        route.setAbGroup(trimToNull(request.getAbGroup()));
        route.setStatus(normalizeConfigStatus(request.getStatus()));
    }

    /**
     * 校验路由引用的模型是否存在。
     *
     * @param request 路由表单
     */
    private void validateRouteModels(AiModelRouteSaveDTO request) {
        getActiveModelByCode(requireText(request.getPrimaryModelCode(), "主模型不能为空"));
        if (StringUtils.hasText(request.getFallbackModelCode())) {
            getActiveModelByCode(request.getFallbackModelCode().trim());
        }
    }

    private LambdaQueryWrapper<AiModelCallLog> buildCallLogWrapper(AiModelCallLogQueryDTO query) {
        LambdaQueryWrapper<AiModelCallLog> wrapper = new LambdaQueryWrapper<AiModelCallLog>()
                .eq(AiModelCallLog::getIsDeleted, NOT_DELETED);
        if (StringUtils.hasText(query.getTraceId())) {
            wrapper.like(AiModelCallLog::getTraceId, query.getTraceId().trim());
        }
        if (query.getUserId() != null) {
            wrapper.eq(AiModelCallLog::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getSceneCode())) {
            wrapper.eq(AiModelCallLog::getSceneCode, query.getSceneCode().trim());
        }
        if (StringUtils.hasText(query.getModelCode())) {
            wrapper.eq(AiModelCallLog::getModelCode, query.getModelCode().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AiModelCallLog::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(AiModelCallLog::getCreateTime, query.getStartTime().trim());
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(AiModelCallLog::getCreateTime, query.getEndTime().trim());
        }
        return wrapper;
    }

    private AiModelConfig getModelRequired(Long id) {
        AiModelConfig model = aiModelConfigMapper.selectById(id);
        if (model == null || Integer.valueOf(DELETED).equals(model.getIsDeleted())) {
            throw new BizException("模型配置不存在");
        }
        return model;
    }

    private AiModelRoute getRouteRequired(Long id) {
        AiModelRoute route = aiModelRouteMapper.selectById(id);
        if (route == null || Integer.valueOf(DELETED).equals(route.getIsDeleted())) {
            throw new BizException("模型路由不存在");
        }
        return route;
    }

    private AiModelConfig getActiveModelByCode(String modelCode) {
        AiModelConfig model = aiModelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelCode, modelCode)
                .eq(AiModelConfig::getStatus, AiConfigStatus.ACTIVE.name())
                .eq(AiModelConfig::getIsDeleted, NOT_DELETED));
        if (model == null) {
            throw new BizException("模型不存在或未启用：" + modelCode);
        }
        return model;
    }

    private boolean existsModelCode(String modelCode, Long excludeId) {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelCode, modelCode)
                .eq(AiModelConfig::getIsDeleted, NOT_DELETED);
        if (excludeId != null) {
            wrapper.ne(AiModelConfig::getId, excludeId);
        }
        return aiModelConfigMapper.selectCount(wrapper) > 0;
    }

    private String resolveApiKey(String requestApiKey, String oldApiKey) {
        if (!StringUtils.hasText(requestApiKey) || requestApiKey.contains("******")) {
            return oldApiKey;
        }
        return requestApiKey.trim();
    }

    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? 1L : pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return 10L;
        }
        return Math.min(pageSize, 100L);
    }

    private Integer normalizeBinary(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private Integer normalizePercent(Integer percent) {
        if (percent == null) {
            return 100;
        }
        return Math.max(0, Math.min(percent, 100));
    }

    private String normalizeConfigStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return AiConfigStatus.ACTIVE.name();
        }
        return AiConfigStatus.DISABLED.name().equals(status.trim())
                ? AiConfigStatus.DISABLED.name()
                : AiConfigStatus.ACTIVE.name();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
