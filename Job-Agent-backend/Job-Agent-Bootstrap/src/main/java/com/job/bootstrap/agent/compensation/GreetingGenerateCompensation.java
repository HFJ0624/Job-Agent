package com.job.bootstrap.agent.compensation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import com.job.bootstrap.mapper.JobCommunicationRecordMapper;
import com.job.bootstrap.service.JobCommunicationRecordService;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.enums.CommunicationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 功能: 打招呼工具补偿动作（任务 G9）。
 *
 * 业务背景:
 * 1. GreetingGenerateTool 会创建沟通记录（GREETING_GENERATED），用户只是“生成了话术”，
 *    还没有去外部平台真发——这个窗口内撤销是无害的；
 * 2. 补偿 = 关闭沟通记录（closeCommunication 幂等）；
 * 3. 状态守卫：只有 GREETING_GENERATED / COPIED 才自动关闭；用户一旦真的沟通了
 *    （COMMUNICATED 及以后），系统不能替用户关掉真实沟通——这是补偿的业务边界。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreetingGenerateCompensation implements AgentCompensationAction {

    /** 允许自动关闭的状态：话术已生成/已复制，但用户尚未真正沟通。 */
    private static final List<String> COMPENSABLE_STATUSES = List.of(
            CommunicationStatus.GREETING_GENERATED.name(),
            CommunicationStatus.COPIED.name()
    );

    private final ObjectMapper objectMapper;
    private final JobCommunicationRecordMapper jobCommunicationRecordMapper;
    private final JobCommunicationRecordService jobCommunicationRecordService;

    @Override
    public String toolName() {
        return "GreetingGenerateTool.generateGreeting";
    }

    @Override
    public CompensationKind kind() {
        return CompensationKind.COMPENSATABLE;
    }

    @Override
    public void compensate(Long userId, Long planId, AgentPlanStepExecutionResult stepResult) {
        Long greetingId = parseGreetingId(toolOutputOf(stepResult));
        if (greetingId == null) {
            throw new IllegalStateException("补偿失败：打招呼输出缺少记录ID");
        }

        /*
         * 按 greetingRecordId 反查沟通记录（generateGreeting 内部自动创建）。
         * 取最新一条：同一话术理论上只关联一条有效沟通记录。
         */
        JobCommunicationRecord record = jobCommunicationRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobCommunicationRecord>()
                        .eq(JobCommunicationRecord::getUserId, userId)
                        .eq(JobCommunicationRecord::getGreetingRecordId, greetingId)
                        .eq(JobCommunicationRecord::getIsDeleted, 0)
                        .orderByDesc(JobCommunicationRecord::getId)
                        .last("limit 1")
        );
        if (record == null) {
            log.debug("补偿跳过：未找到关联沟通记录，userId={}, greetingId={}", userId, greetingId);
            return;
        }

        if (!COMPENSABLE_STATUSES.contains(record.getCommunicationStatus())) {
            // 已进入真实沟通流程：不替用户关闭，仅记录事实
            log.info("补偿跳过：沟通已进入真实流程，userId={}, communicationId={}, status={}",
                    userId, record.getId(), record.getCommunicationStatus());
            return;
        }

        jobCommunicationRecordService.closeCommunication(userId, record.getId());
        log.info("补偿完成：沟通记录已关闭，userId={}, communicationId={}, planId={}, stepNo={}",
                userId, record.getId(), planId, stepResult == null ? null : stepResult.getStepNo());
    }

    private String toolOutputOf(AgentPlanStepExecutionResult stepResult) {
        if (stepResult == null || stepResult.getToolResult() == null) {
            return null;
        }
        return stepResult.getToolResult().getDataJson();
    }

    // 从工具输出 JSON 提取话术记录 ID（GreetingVO.id）
    private Long parseGreetingId(String toolOutputJson) {
        if (!StringUtils.hasText(toolOutputJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(toolOutputJson);
            JsonNode idNode = root.path("id");
            if (!idNode.isNumber()) {
                return null;
            }
            return idNode.asLong();
        } catch (Exception exception) {
            log.debug("补偿解析打招呼输出失败，error={}", exception.getMessage());
            return null;
        }
    }
}
