package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.ai.AiGatewayProperties;
import com.job.bootstrap.ai.AiModelCallResponse;
import com.job.bootstrap.ai.AiModelHttpClient;
import com.job.bootstrap.ai.AiModelRateLimiter;
import com.job.bootstrap.ai.AiRetryPolicy;
import com.job.bootstrap.ai.AiRenderedPrompt;
import com.job.bootstrap.mapper.AiModelCallLogMapper;
import com.job.bootstrap.mapper.AiModelCircuitStateMapper;
import com.job.bootstrap.mapper.AiModelConfigMapper;
import com.job.bootstrap.mapper.AiModelRouteMapper;
import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.AiPromptRuntimeService;
import com.job.common.entity.ai.AiModelCallLog;
import com.job.common.entity.ai.AiModelCircuitState;
import com.job.common.entity.ai.AiModelConfig;
import com.job.common.entity.ai.AiModelRoute;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.enums.AgentObservationErrorCategory;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import com.job.enums.AiCircuitStatus;
import com.job.enums.AiConfigStatus;
import com.job.enums.AiModelCallStatus;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型统一调用网关实现，是企业级模型路由、Prompt 渲染、重试、熔断与成本观测的核心入口。
 *
 * <p>核心职责：
 * 1. 按业务场景（sceneCode）解析模型路由（ai_model_route），支持灰度与 A/B 分流。
 * 2. 调用 AiPromptRuntimeService 渲染 Prompt（支持固定版本与最新已发布版本）。
 * 3. 按主模型 + 备用模型顺序调用，单模型按配置重试。
 * 4. 维护模型熔断状态（CLOSED/OPEN），失败累计达阈值后熔断，冷却时间过后自动恢复。
 * 5. 每次调用写入 ai_model_call_log，记录 token、费用、耗时，并同步 Observation 事件。
 * 6. 模型熔断时不真实请求供应商，但仍写 Observation 事件便于排障。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 AI Gateway Service 层。</p>
 *
 * <p>主要调用链：
 * AgentChatServiceImpl.summarizeExecutorResult -> AiModelGatewayServiceImpl.chat (AGENT_SUMMARY)
 * AgentDailyReportAiComposer.compose -> AiModelGatewayServiceImpl.chat (AGENT_DAILY_REPORT)
 * -> resolveRoute (路由解析 + 灰度/A-B)
 * -> AiPromptRuntimeService.renderPrompt (Prompt 渲染)
 * -> callOpenAiCompatibleModel (HTTP 调用 OpenAI 兼容接口)
 * -> recordCallLog (写 ai_model_call_log)
 * -> AgentObservationService.recordEvent (写 MODEL 观测事件)
 * -> openCircuitIfNeeded / closeCircuit (熔断状态维护)</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>AiPromptRuntimeService 负责 Prompt 模板与版本解析；</li>
 *   <li>ai_model_route / ai_model_config / ai_model_circuit_state 三张表支撑路由、模型与熔断；</li>
 *   <li>AgentObservationService 接收 MODEL 观测事件，与 Trace 互补；</li>
 *   <li>调用方（AgentChatServiceImpl）在网关失败时降级为确定性兜底摘要。</li>
 * </ul></p>
 *
 * <p>模型调用与重试机制说明：
 * 外层循环遍历 [主模型, 备用模型]，内层循环按 maxRetries+1 重试；
 * 单次失败累计熔断计数，达阈值后 OPEN，冷却时间过后试探恢复（CLOSED）；
 * 全部模型重试用完仍失败则抛 BizException，由调用方降级。</p>
 *
 * 作者:hfj
 * 日期:2026/6/21
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiModelGatewayServiceImpl implements AiModelGatewayService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final String DEFAULT_CHAT_PATH = "/chat/completions";

    private final AiModelRouteMapper aiModelRouteMapper;
    private final AiModelConfigMapper aiModelConfigMapper;
    private final AiModelCallLogMapper aiModelCallLogMapper;
    private final AiModelCircuitStateMapper aiModelCircuitStateMapper;
    private final AiPromptRuntimeService aiPromptRuntimeService;
    private final AgentObservationService agentObservationService;
    private final ObjectMapper objectMapper;
    private final AiModelHttpClient aiModelHttpClient;
    private final AiModelRateLimiter aiModelRateLimiter;
    private final AiRetryPolicy aiRetryPolicy;
    private final AiGatewayProperties aiGatewayProperties;

    /**
     * 按业务场景调用模型。
     *
     * 方法步骤:
     * 1. 根据 sceneCode 找到启用的模型路由。
     * 2. 根据路由解析 Prompt 版本并完成变量渲染。
     * 3. 先调用主模型，主模型失败后再调用备用模型。
     * 4. 每个模型按配置重试，失败会累计熔断状态，成功会重置熔断。
     * 5. 每一次真实调用都会写入 ai_model_call_log，方便后台看 token、耗时和成本。
     *
     * @param sceneCode 业务场景编码
     * @param variables Prompt 变量
     * @param userMessage 用户消息
     * @param userId 用户 ID
     * @param traceId 链路 ID
     * @return 模型输出文本
     */
    @Override
    public String chat(String sceneCode, Map<String, Object> variables, String userMessage, Long userId, String traceId) {
        AiModelRoute route = resolveRoute(sceneCode, variables, userId, traceId);
        AiRenderedPrompt renderedPrompt = aiPromptRuntimeService.renderPrompt(route, variables);
        List<AiModelConfig> modelCandidates = resolveModelCandidates(route);

        /*
         * 限流（任务8）：场景 + 用户双维度令牌桶，拿不到许可即快速失败，
         * 由调用方走降级（如确定性兜底摘要），避免请求堆积打爆供应商。
         */
        if (!aiModelRateLimiter.tryAcquireScene(sceneCode) || !aiModelRateLimiter.tryAcquireUser(userId)) {
            throw new BizException("触发模型网关限流，请稍后重试");
        }

        Exception lastException = null;
        for (int modelIndex = 0; modelIndex < modelCandidates.size(); modelIndex++) {
            AiModelConfig model = modelCandidates.get(modelIndex);
            boolean fallbackUsed = modelIndex > 0;

            if (isCircuitOpen(model)) {
                lastException = new BizException("模型熔断中：" + model.getModelCode());
                recordCircuitOpenObservation(
                        traceId,
                        userId,
                        sceneCode,
                        route.getPromptCode(),
                        renderedPrompt.promptVersion().getId(),
                        model,
                        fallbackUsed
                );
                continue;
            }

            int maxAttempts = Math.max(1, safeInt(model.getMaxRetries(), 0) + 1);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                long start = System.currentTimeMillis();
                try {
                    AiModelCallResponse response = aiModelHttpClient.chat(model, renderedPrompt.systemPrompt(), userMessage);
                    recordCallLog(
                            traceId,
                            userId,
                            sceneCode,
                            route.getPromptCode(),
                            renderedPrompt.promptVersion().getId(),
                            model,
                            fallbackUsed,
                            response.inputTokens(),
                            response.outputTokens(),
                            System.currentTimeMillis() - start,
                            AiModelCallStatus.SUCCESS.name(),
                            null
                    );
                    closeCircuit(model);
                    return response.content();
                } catch (Exception exception) {
                    lastException = exception;
                    recordCallLog(
                            traceId,
                            userId,
                            sceneCode,
                            route.getPromptCode(),
                            renderedPrompt.promptVersion().getId(),
                            model,
                            fallbackUsed,
                            estimateTokens(renderedPrompt.systemPrompt() + "\n" + userMessage),
                            0,
                            System.currentTimeMillis() - start,
                            AiModelCallStatus.FAILED.name(),
                            exception.getMessage()
                    );
                    openCircuitIfNeeded(model, exception);

                    boolean retryable = aiRetryPolicy.isRetryable(exception);
                    if (attempt < maxAttempts && retryable) {
                        // 可重试错误（429/5xx/超时）：指数退避后重试
                        long backoffMs = aiRetryPolicy.backoffDelayMs(attempt);
                        log.warn("AI 模型调用失败，退避后重试，modelCode={}, attempt={}/{}, backoffMs={}, error={}",
                                model.getModelCode(), attempt, maxAttempts, backoffMs, exception.getMessage());
                        sleep(backoffMs);
                    } else if (attempt < maxAttempts) {
                        // 4xx 等不可重试错误：不重试，直接结束本模型，切备用模型
                        log.warn("AI 模型调用失败且不可重试，切换备用模型，modelCode={}, attempt={}/{}, error={}",
                                model.getModelCode(), attempt, maxAttempts, exception.getMessage());
                        break;
                    }
                }
            }
        }

        throw new BizException("AI 模型调用失败：" + (lastException == null ? "没有可用模型" : lastException.getMessage()));
    }

    // 退避 sleep：被中断时恢复中断标记，避免吞掉中断信号
    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析当前业务场景的模型路由。
     *
     * @param sceneCode 业务场景编码
     * @param variables Prompt 变量
     * @param userId 用户 ID
     * @param traceId 链路 ID
     * @return 命中的模型路由
     */
    private AiModelRoute resolveRoute(String sceneCode, Map<String, Object> variables, Long userId, String traceId) {
        if (!StringUtils.hasText(sceneCode)) {
            throw new BizException("模型场景不能为空");
        }

        List<AiModelRoute> routes = aiModelRouteMapper.selectList(new LambdaQueryWrapper<AiModelRoute>()
                .eq(AiModelRoute::getSceneCode, sceneCode.trim())
                .eq(AiModelRoute::getStatus, AiConfigStatus.ACTIVE.name())
                .eq(AiModelRoute::getIsDeleted, NOT_DELETED)
                .orderByDesc(AiModelRoute::getCreateTime));
        if (CollectionUtils.isEmpty(routes)) {
            throw new BizException("没有启用的模型路由：" + sceneCode);
        }

        /*
         * 第一版灰度/A-B 路由规则:
         * 1. 灰度比例按 userId/traceId 做稳定哈希，同一用户同一链路命中结果相对稳定。
         * 2. abGroup 只有在变量中显式传入时才过滤，避免没接 A/B 参数的老链路不可用。
         * 3. 如果都未命中，则兜底使用最新启用路由。
         */
        String abGroup = variables == null || variables.get("abGroup") == null
                ? null
                : String.valueOf(variables.get("abGroup"));
        int bucket = stableBucket(userId, traceId, sceneCode);
        for (AiModelRoute route : routes) {
            boolean abMatched = !StringUtils.hasText(abGroup)
                    || !StringUtils.hasText(route.getAbGroup())
                    || abGroup.equals(route.getAbGroup());
            boolean grayMatched = bucket < safeInt(route.getGrayPercent(), 100);
            if (abMatched && grayMatched) {
                return route;
            }
        }
        return routes.get(0);
    }

    /**
     * 解析主模型和备用模型。
     *
     * @param route 模型路由
     * @return 模型候选列表
     */
    private List<AiModelConfig> resolveModelCandidates(AiModelRoute route) {
        List<AiModelConfig> models = new ArrayList<>();
        models.add(getActiveModel(route.getPrimaryModelCode()));

        if (StringUtils.hasText(route.getFallbackModelCode())
                && !route.getFallbackModelCode().equals(route.getPrimaryModelCode())) {
            models.add(getActiveModel(route.getFallbackModelCode()));
        }
        return models;
    }

    /**
     * 写入模型调用日志。
     *
     * @param traceId 链路 ID
     * @param userId 用户 ID
     * @param sceneCode 场景编码
     * @param promptCode Prompt 编码
     * @param promptVersionId Prompt 版本 ID
     * @param model 模型配置
     * @param fallbackUsed 是否使用备用模型
     * @param inputTokens 输入 token
     * @param outputTokens 输出 token
     * @param costTime 耗时
     * @param status 状态
     * @param errorMsg 错误信息
     */
    private void recordCallLog(
            String traceId,
            Long userId,
            String sceneCode,
            String promptCode,
            Long promptVersionId,
            AiModelConfig model,
            boolean fallbackUsed,
            Integer inputTokens,
            Integer outputTokens,
            Long costTime,
            String status,
            String errorMsg
    ) {
        int safeInputTokens = safeInt(inputTokens, 0);
        int safeOutputTokens = safeInt(outputTokens, 0);
        BigDecimal inputCost = calculateCost(safeInputTokens, model.getInputPricePer1k());
        BigDecimal outputCost = calculateCost(safeOutputTokens, model.getOutputPricePer1k());

        Date now = new Date();
        AiModelCallLog log = new AiModelCallLog();
        log.setTraceId(traceId);
        log.setUserId(userId);
        log.setSceneCode(sceneCode);
        log.setPromptCode(promptCode);
        log.setPromptVersionId(promptVersionId);
        log.setModelCode(model.getModelCode());
        log.setFallbackUsed(fallbackUsed ? 1 : 0);
        log.setInputTokens(safeInputTokens);
        log.setOutputTokens(safeOutputTokens);
        log.setTotalTokens(safeInputTokens + safeOutputTokens);
        log.setInputCost(inputCost);
        log.setOutputCost(outputCost);
        log.setTotalCost(inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP));
        log.setCostTime(costTime);
        log.setStatus(status);
        log.setErrorMsg(limitText(errorMsg, 1000));
        log.setIsDeleted(NOT_DELETED);
        log.setCreateTime(now);
        log.setUpdateTime(now);
        aiModelCallLogMapper.insert(log);
        recordModelObservation(log, model, promptCode, promptVersionId, fallbackUsed, errorMsg);
    }

    /**
     * 同步写入模型调用观测事件。
     *
     * 方法步骤:
     * 1. 复用 ai_model_call_log 已经计算好的 token、费用和耗时，避免重复计算。
     * 2. 请求快照只保存路由和 Prompt 元信息，不保存完整 Prompt，减少敏感信息暴露。
     * 3. 失败时写入统一失败分类，后台可以直接按 MODEL_ERROR 查询。
     *
     * @param log 模型调用日志
     * @param model 模型配置
     * @param promptCode Prompt 编码
     * @param promptVersionId Prompt 版本 ID
     * @param fallbackUsed 是否使用备用模型
     * @param errorMsg 错误信息
     */
    private void recordModelObservation(
            AiModelCallLog log,
            AiModelConfig model,
            String promptCode,
            Long promptVersionId,
            boolean fallbackUsed,
            String errorMsg
    ) {
        boolean success = AiModelCallStatus.SUCCESS.name().equals(log.getStatus());

        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("sceneCode", log.getSceneCode());
        requestSnapshot.put("promptCode", promptCode);
        requestSnapshot.put("promptVersionId", promptVersionId);
        requestSnapshot.put("provider", model.getProvider());
        requestSnapshot.put("modelIdentifier", model.getModelIdentifier());
        requestSnapshot.put("fallbackUsed", fallbackUsed);

        Map<String, Object> responseSnapshot = new LinkedHashMap<>();
        responseSnapshot.put("status", log.getStatus());
        responseSnapshot.put("errorMsg", errorMsg);

        agentObservationService.recordEvent(AgentObservationRecord.builder()
                .traceId(log.getTraceId())
                .userId(log.getUserId())
                .sceneCode(log.getSceneCode())
                .eventType(AgentObservationEventType.MODEL)
                .eventName(log.getSceneCode())
                .status(success ? AgentObservationStatus.SUCCESS : AgentObservationStatus.FAILED)
                .errorCategory(success ? AgentObservationErrorCategory.NONE : AgentObservationErrorCategory.MODEL_ERROR)
                .errorCode(success ? null : "MODEL_CALL_FAILED")
                .errorMsg(errorMsg)
                .modelCode(log.getModelCode())
                .inputTokens(log.getInputTokens())
                .outputTokens(log.getOutputTokens())
                .totalTokens(log.getTotalTokens())
                .totalCost(log.getTotalCost())
                .durationMs(log.getCostTime())
                .requestSnapshot(requestSnapshot)
                .responseSnapshot(responseSnapshot)
                .build());
    }

    /**
     * 记录模型熔断事件。
     *
     * 说明:
     * 1. 熔断时不会真正请求供应商，因此不写 ai_model_call_log。
     * 2. 但它属于线上排障的关键事件，必须写入统一观测表。
     */
    private void recordCircuitOpenObservation(
            String traceId,
            Long userId,
            String sceneCode,
            String promptCode,
            Long promptVersionId,
            AiModelConfig model,
            boolean fallbackUsed
    ) {
        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("sceneCode", sceneCode);
        requestSnapshot.put("promptCode", promptCode);
        requestSnapshot.put("promptVersionId", promptVersionId);
        requestSnapshot.put("fallbackUsed", fallbackUsed);

        agentObservationService.recordEvent(AgentObservationRecord.builder()
                .traceId(traceId)
                .userId(userId)
                .sceneCode(sceneCode)
                .eventType(AgentObservationEventType.MODEL)
                .eventName(sceneCode)
                .status(AgentObservationStatus.FAILED)
                .errorCategory(AgentObservationErrorCategory.MODEL_ERROR)
                .errorCode("MODEL_CIRCUIT_OPEN")
                .errorMsg("模型熔断中：" + model.getModelCode())
                .modelCode(model.getModelCode())
                .durationMs(0L)
                .requestSnapshot(requestSnapshot)
                .build());
    }

    /**
     * 判断模型熔断是否打开。
     *
     * @param model 模型配置
     * @return true 表示当前不能调用
     */
    private boolean isCircuitOpen(AiModelConfig model) {
        if (!Integer.valueOf(1).equals(model.getCircuitEnabled())) {
            return false;
        }

        AiModelCircuitState state = getCircuitState(model.getModelCode());
        if (state == null || AiCircuitStatus.CLOSED.name().equals(state.getCircuitStatus())) {
            return false;
        }
        // 已有请求在探测（HALF_OPEN），其余请求继续被拒，避免并发探测
        if (AiCircuitStatus.HALF_OPEN.name().equals(state.getCircuitStatus())) {
            return true;
        }

        // OPEN：冷却时间未过则保持打开
        Date now = new Date();
        if (state.getOpenedUntil() != null && state.getOpenedUntil().after(now)) {
            return true;
        }

        /*
         * 冷却已过：条件更新 OPEN -> HALF_OPEN，只有 1 个请求抢到探测资格（放行），
         * 其余请求因没抢到而继续被拒；探测结果由 closeCircuit（成功）/ openCircuitIfNeeded（失败）落地。
         */
        int probed = aiModelCircuitStateMapper.update(null, new UpdateWrapper<AiModelCircuitState>()
                .set("circuit_status", AiCircuitStatus.HALF_OPEN.name())
                .eq("id", state.getId())
                .eq("circuit_status", AiCircuitStatus.OPEN.name()));
        if (probed > 0) {
            log.info("AI 模型熔断进入 HALF_OPEN，放行 1 个探测请求，modelCode={}", model.getModelCode());
            return false;
        }
        return true;
    }

    /**
     * 模型调用成功后关闭熔断。
     *
     * @param model 模型配置
     */
    private void closeCircuit(AiModelConfig model) {
        AiModelCircuitState state = getCircuitState(model.getModelCode());
        if (state == null) {
            return;
        }

        state.setFailureCount(0);
        state.setCircuitStatus(AiCircuitStatus.CLOSED.name());
        state.setOpenedUntil(null);
        state.setUpdateTime(new Date());
        aiModelCircuitStateMapper.updateById(state);
    }

    /**
     * 失败后累计熔断状态。
     *
     * @param model 模型配置
     * @param exception 失败异常
     */
    private void openCircuitIfNeeded(AiModelConfig model, Exception exception) {
        if (!Integer.valueOf(1).equals(model.getCircuitEnabled())) {
            return;
        }

        Date now = new Date();
        AiModelCircuitState state = getCircuitState(model.getModelCode());
        if (state == null) {
            state = new AiModelCircuitState();
            state.setModelCode(model.getModelCode());
            state.setFailureCount(0);
            state.setCircuitStatus(AiCircuitStatus.CLOSED.name());
            state.setIsDeleted(NOT_DELETED);
            state.setCreateTime(now);
        }

        int failureCount = safeInt(state.getFailureCount(), 0) + 1;
        state.setFailureCount(failureCount);
        state.setLastFailureTime(now);
        state.setUpdateTime(now);

        // HALF_OPEN 探测失败：立即重新打开（不必等失败累计到阈值）
        boolean halfOpenProbeFailed = AiCircuitStatus.HALF_OPEN.name().equals(state.getCircuitStatus());
        if (halfOpenProbeFailed || failureCount >= safeInt(model.getFailureThreshold(), 3)) {
            state.setCircuitStatus(AiCircuitStatus.OPEN.name());
            state.setOpenedUntil(new Date(now.getTime() + safeInt(model.getCooldownSeconds(), 60) * 1000L));
            log.warn("AI 模型熔断打开，modelCode={}, failureCount={}, halfOpenProbeFailed={}, reason={}",
                    model.getModelCode(),
                    failureCount,
                    halfOpenProbeFailed,
                    exception.getMessage());
        }

        if (state.getId() == null) {
            aiModelCircuitStateMapper.insert(state);
        } else {
            aiModelCircuitStateMapper.updateById(state);
        }
    }

    private AiModelCircuitState getCircuitState(String modelCode) {
        return aiModelCircuitStateMapper.selectOne(new LambdaQueryWrapper<AiModelCircuitState>()
                .eq(AiModelCircuitState::getModelCode, modelCode)
                .eq(AiModelCircuitState::getIsDeleted, NOT_DELETED));
    }

    private AiModelConfig getActiveModel(String modelCode) {
        AiModelConfig model = aiModelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelCode, modelCode)
                .eq(AiModelConfig::getStatus, AiConfigStatus.ACTIVE.name())
                .eq(AiModelConfig::getIsDeleted, NOT_DELETED));
        if (model == null) {
            throw new BizException("模型不存在或未启用：" + modelCode);
        }
        return model;
    }

    private BigDecimal calculateCost(int tokens, BigDecimal pricePer1k) {
        if (tokens <= 0 || pricePer1k == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return pricePer1k
                .multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private int stableBucket(Long userId, String traceId, String sceneCode) {
        String seed = (userId == null ? "" : userId)
                + "|"
                + (StringUtils.hasText(traceId) ? traceId : "")
                + "|"
                + sceneCode;
        return Math.floorMod(seed.hashCode(), 100);
    }

    private int safeInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
