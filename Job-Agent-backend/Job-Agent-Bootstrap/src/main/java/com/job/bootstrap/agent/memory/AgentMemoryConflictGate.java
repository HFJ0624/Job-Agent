package com.job.bootstrap.agent.memory;

import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 长期记忆写入门控（任务 G7，写入前冲突预检）。
 *
 * 设计说明:
 * 1. 位置在“写入策略校验之后、落库之前”：WritePolicy 管“这条候选值不值得记”，
 *    本门控管“这条候选和库里已有记忆冲不冲突”，两者职责不重叠；
 * 2. 与 AgentMemoryServiceImpl 的同 key 冲突裁决分工：
 *    那个是“同 key 覆盖时”的后置置信度裁决；本门控是写入前的预检，
 *    覆盖它管不到的三种情况（等价值抖动、临时性表达、同类型异 key 语义重叠）；
 * 3. 三条拦截规则（全部确定性规则，不引入模型，保证可解释、可单测）:
 *    - SKIP_EQUIVALENT：同类型同 key 已有等价值记忆（“上海”vs“上海吧”）→ 跳过，
 *      避免抽取抖动反复覆盖、刷冲突历史和画像重建；
 *    - DEFER_TEMPORARY：值含临时性表达（“今天/暂时/这两天”）→ 跳过，
 *      避免把临时状态当长期事实；
 *    - CONFLICT_REVIEW：同类型不同 key 但值重叠（“Java开发”vs“Java开发工程师”）→
 *      跳过并留观测事件，为什么不自动合并：误合并比不合并更难恢复，交给后台人工裁决；
 * 4. 防御性降级：查询历史记忆异常时返回 PASS——门控自身故障绝不能挡住记忆主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMemoryConflictGate {

    /** 临时性表达词：命中则视为短期状态，不写长期事实。 */
    private static final List<String> TEMPORARY_WORDS = List.of(
            "今天", "今日", "这两天", "这几天", "暂时", "暂时先", "此刻", "当下", "眼下", "近期先"
    );

    /** 中文尾语气词：归一化时去掉，保证“上海”与“上海吧”判等。 */
    private static final List<String> MODAL_PARTICLES = List.of("吧", "啊", "呀", "呢", "哦", "哈", "嘛", "了", "的");

    /** 值重叠判定的最小长度：太短的值（如“上海”二字）不做包含判定，避免误伤。 */
    private static final int OVERLAP_MIN_LENGTH = 4;

    private final AgentMemoryService agentMemoryService;
    private final AgentObservationService agentObservationService;

    /**
     * 写入前冲突预检。
     *
     * @param userId    用户 ID
     * @param traceId   链路 ID（用于观测事件关联）
     * @param candidate 候选记忆
     * @return 门控决策；PASS 才允许继续落库
     */
    public MemoryGateDecision check(Long userId, String traceId, AgentMemoryCandidate candidate) {
        if (userId == null || userId <= 0 || candidate == null || candidate.getMemoryType() == null) {
            return MemoryGateDecision.PASS;
        }

        List<AgentMemoryVO> existingMemories;
        try {
            existingMemories = agentMemoryService.listActiveMemoriesByType(userId, candidate.getMemoryType());
        } catch (Exception exception) {
            // 门控自身故障不挡主流程：降级放行，仅留日志
            log.warn("记忆门控查询历史失败，降级放行，userId={}, error={}", userId, exception.getMessage());
            return MemoryGateDecision.PASS;
        }
        String normalizedValue = normalizeValue(candidate.getMemoryValue());
        if (!StringUtils.hasText(normalizedValue)) {
            return MemoryGateDecision.PASS;
        }

        /*
         * 规则 1：临时性表达——只取决于候选值本身，与历史记忆无关，
         * 必须放在“无历史早返回”之前，否则用户没有任何历史记忆时会漏拦。
         */
        if (containsTemporaryWord(candidate.getMemoryValue())) {
            return block(userId, traceId, candidate,
                    new MemoryGateDecision(GateResult.DEFER_TEMPORARY, "值包含临时性表达，不写入长期事实"),
                    "值包含临时性表达，不写入长期事实");
        }

        // 无历史记忆：剩余两条规则都依赖历史，直接放行
        if (existingMemories.isEmpty()) {
            return MemoryGateDecision.PASS;
        }

        for (AgentMemoryVO existing : existingMemories) {
            // 规则 2：同 key 等价值（抽取抖动），跳过
            if (isSameKey(candidate.getMemoryKey(), existing.getMemoryKey())
                    && normalizedValue.equals(normalizeValue(existing.getMemoryValue()))) {
                return block(userId, traceId, candidate,
                        new MemoryGateDecision(GateResult.SKIP_EQUIVALENT, "同 key 等价值记忆已存在，跳过重复写入"),
                        "同 key 等价值记忆已存在，跳过重复写入");
            }
        }

        // 规则 3：同类型异 key 值重叠，留后台裁决
        for (AgentMemoryVO existing : existingMemories) {
            if (isSameKey(candidate.getMemoryKey(), existing.getMemoryKey())) {
                continue;
            }
            if (isValueOverlapping(normalizedValue, normalizeValue(existing.getMemoryValue()))) {
                String reason = "与异 key 记忆值重叠（" + existing.getMemoryKey() + "），留后台裁决";
                return block(userId, traceId, candidate,
                        new MemoryGateDecision(GateResult.CONFLICT_REVIEW, reason), reason);
            }
        }

        return MemoryGateDecision.PASS;
    }

    // 拦截：记观测事件（后台可统计门控拦截量）并返回决策
    private MemoryGateDecision block(
            Long userId,
            String traceId,
            AgentMemoryCandidate candidate,
            MemoryGateDecision decision,
            String reason
    ) {
        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("memoryType", candidate.getMemoryType().name());
        requestSnapshot.put("memoryKey", candidate.getMemoryKey());
        requestSnapshot.put("decision", decision.result().name());
        requestSnapshot.put("reason", reason);

        try {
            agentObservationService.recordEvent(AgentObservationRecord.builder()
                    .traceId(traceId)
                    .userId(userId)
                    .eventType(AgentObservationEventType.MEMORY)
                    .eventName("MemoryGateBlock")
                    .status(AgentObservationStatus.SUCCESS)
                    .requestSnapshot(requestSnapshot)
                    .build());
        } catch (Exception exception) {
            // 观测失败不影响门控决策本身
            log.debug("记忆门控观测事件记录失败，userId={}, error={}", userId, exception.getMessage());
        }

        log.debug("记忆门控拦截，userId={}, decision={}, reason={}", userId, decision.result(), reason);
        return decision;
    }

    private boolean containsTemporaryWord(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String word : TEMPORARY_WORDS) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameKey(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    /*
     * 值重叠判定（保守）:
     * 1. 归一化后完全相等 → 重叠；
     * 2. 一方包含另一方且较短者长度达到阈值（>=4）→ 重叠；
     * 3. 短值（如“上海”）不触发包含判定，避免“上海”被“上海浦东张江”误拦。
     */
    private boolean isValueOverlapping(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        String shorter = left.length() <= right.length() ? left : right;
        String longer = left.length() <= right.length() ? right : left;
        return shorter.length() >= OVERLAP_MIN_LENGTH && longer.contains(shorter);
    }

    // 归一化：去首尾空白 + 去尾语气词 + 小写，用于“等价”判定
    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        boolean changed = true;
        while (changed && !normalized.isEmpty()) {
            changed = false;
            for (String particle : MODAL_PARTICLES) {
                if (normalized.endsWith(particle)) {
                    normalized = normalized.substring(0, normalized.length() - particle.length());
                    changed = true;
                }
            }
        }
        return normalized;
    }

    /**
     * 门控决策。
     *
     * @param result 决策结果
     * @param reason 决策原因（便于日志与观测排查）
     */
    public record MemoryGateDecision(GateResult result, String reason) {

        /** 放行。 */
        public static final MemoryGateDecision PASS = new MemoryGateDecision(GateResult.PASS, "");

        public boolean blocked() {
            return result != GateResult.PASS;
        }
    }

    /** 门控结果枚举。 */
    public enum GateResult {
        /** 放行落库。 */
        PASS,
        /** 同 key 等价值，跳过重复写入。 */
        SKIP_EQUIVALENT,
        /** 临时性表达，暂不写入长期事实。 */
        DEFER_TEMPORARY,
        /** 同类型异 key 值重叠，留后台裁决。 */
        CONFLICT_REVIEW
    }
}
