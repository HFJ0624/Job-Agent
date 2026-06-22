package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.ai.AiModelCallResponse;
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
 * 作者:hfj
 * 功能:AI 模型统一调用网关实现
 * 日期:2026/6/21
 */
@Slf4j
@Service
@RequiredArgsConstructor
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
                    AiModelCallResponse response = callOpenAiCompatibleModel(model, renderedPrompt.systemPrompt(), userMessage);
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

                    /*
                     * 同一个模型还有重试次数时继续尝试。
                     * 当前模型重试用完后，外层循环才会切换到备用模型。
                     */
                    if (attempt < maxAttempts) {
                        log.warn("AI 模型调用失败，准备重试，modelCode={}, attempt={}/{}", model.getModelCode(), attempt, maxAttempts, exception);
                    }
                }
            }
        }

        throw new BizException("AI 模型调用失败：" + (lastException == null ? "没有可用模型" : lastException.getMessage()));
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
     * 调用 OpenAI 兼容模型接口。
     *
     * @param model 模型配置
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return 调用结果
     */
    private AiModelCallResponse callOpenAiCompatibleModel(AiModelConfig model, String systemPrompt, String userMessage) {
        if (!StringUtils.hasText(model.getApiKey())) {
            throw new BizException("模型 API Key 为空：" + model.getModelCode());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.getModelIdentifier());
        body.put("temperature", model.getTemperature());
        body.put("max_tokens", model.getMaxTokens());
        body.put("messages", List.of(
                Map.of("role", "system", "content", safeText(systemPrompt)),
                Map.of("role", "user", "content", safeText(userMessage))
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(model.getApiKey());

        RestTemplate restTemplate = buildRestTemplate(model);
        ResponseEntity<String> response = restTemplate.exchange(
                buildChatUrl(model),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        return parseModelResponse(response.getBody(), systemPrompt, userMessage);
    }

    /**
     * 解析 OpenAI 兼容响应。
     *
     * @param responseBody 响应 JSON
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return 模型调用结果
     */
    private AiModelCallResponse parseModelResponse(String responseBody, String systemPrompt, String userMessage) {
        if (!StringUtils.hasText(responseBody)) {
            throw new BizException("模型响应为空");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (!StringUtils.hasText(content)) {
                throw new BizException("模型响应没有 content");
            }

            int inputTokens = root.path("usage").path("prompt_tokens").asInt(estimateTokens(systemPrompt + "\n" + userMessage));
            int outputTokens = root.path("usage").path("completion_tokens").asInt(estimateTokens(content));
            return new AiModelCallResponse(content, inputTokens, outputTokens);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(
                    ResultCodeEnum.SYSTEM_ERROR.getCode(),
                    "模型响应解析失败：" + exception.getMessage(),
                    exception
            );
        }
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
        if (state == null || !AiCircuitStatus.OPEN.name().equals(state.getCircuitStatus())) {
            return false;
        }

        Date now = new Date();
        if (state.getOpenedUntil() != null && state.getOpenedUntil().after(now)) {
            return true;
        }

        /*
         * 冷却时间已过，关闭熔断并允许本次请求试探恢复。
         * 第一版不单独实现 HALF_OPEN 状态，先用简单闭环保证主流程可靠。
         */
        state.setCircuitStatus(AiCircuitStatus.CLOSED.name());
        state.setFailureCount(0);
        state.setUpdateTime(now);
        aiModelCircuitStateMapper.updateById(state);
        return false;
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

        if (failureCount >= safeInt(model.getFailureThreshold(), 3)) {
            state.setCircuitStatus(AiCircuitStatus.OPEN.name());
            state.setOpenedUntil(new Date(now.getTime() + safeInt(model.getCooldownSeconds(), 60) * 1000L));
            log.warn("AI 模型熔断打开，modelCode={}, failureCount={}, reason={}",
                    model.getModelCode(),
                    failureCount,
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

    private RestTemplate buildRestTemplate(AiModelConfig model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(safeInt(model.getTimeoutSeconds(), 45));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private String buildChatUrl(AiModelConfig model) {
        String baseUrl = model.getBaseUrl().trim();
        String chatPath = StringUtils.hasText(model.getChatPath()) ? model.getChatPath().trim() : DEFAULT_CHAT_PATH;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!chatPath.startsWith("/")) {
            chatPath = "/" + chatPath;
        }
        return baseUrl + chatPath;
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

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
