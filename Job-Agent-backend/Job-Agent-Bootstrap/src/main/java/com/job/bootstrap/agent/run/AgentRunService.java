package com.job.bootstrap.agent.run;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.job.bootstrap.mapper.AgentRunMapper;
import com.job.common.entity.agent.AgentRun;
import com.job.enums.AgentRunStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

/**
 * 功能: Agent run 状态服务（第一阶段 ReAct + Workflow）。
 *
 * 设计说明（对应文档“状态与结果保存”）:
 * 1. **短事务**：创建、领取、写回各自独立提交——模型调用与工具执行必须在事务外，
 *    本类不提供“包住整条链路”的事务方法；
 * 2. **原子领取**：条件更新（PENDING/WAITING_INPUT → RUNNING 且 attempt 匹配），
 *    影响行数为 0 表示已被其他执行器领取，本次放弃；
 * 3. **持有权校验**：所有写回都带 attempt 条件——旧执行器（attempt 已过期）晚回写影响 0 行，
 *    不会覆盖新结果；
 * 4. 本阶段不引入分布式租约平台，超时重试用“显式重试”入口（attempt 递增）即可。
 */
@Slf4j
@Service
public class AgentRunService {

    private static final int NOT_DELETED = 0;

    /** 过期 RUNNING 判定阈值（毫秒）：显式重试允许接管。 */
    private static final long STALE_RUNNING_MILLIS = 10 * 60_000L;

    private final AgentRunMapper agentRunMapper;
    private final TransactionTemplate transactionTemplate;

    public AgentRunService(AgentRunMapper agentRunMapper, PlatformTransactionManager transactionManager) {
        this.agentRunMapper = agentRunMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 创建 run（短事务）。状态 PENDING，等待领取。
     */
    public AgentRun create(Long userId,
                           Long conversationId,
                           Long planId,
                           String traceId,
                           String agentName,
                           String inputText,
                           String constraintsJson) {
        AgentRun run = new AgentRun();
        run.setUserId(userId);
        run.setConversationId(conversationId);
        run.setPlanId(planId);
        run.setTraceId(traceId);
        run.setAgentName(agentName);
        run.setStatus(AgentRunStatus.PENDING.name());
        run.setInputText(inputText);
        run.setConstraintsJson(constraintsJson);
        run.setAttempt(0);
        run.setVersion(0);
        run.setIsDeleted(NOT_DELETED);
        run.setCreateTime(new Date());
        run.setUpdateTime(new Date());

        transactionTemplate.executeWithoutResult(status -> agentRunMapper.insert(run));
        return run;
    }

    /**
     * 节点化创建（第二阶段）：一个父计划下多个子 run，用 planId + nodeKey + inputVersion 定位。
     */
    public AgentRun createForNode(Long userId, Long conversationId, Long planId, String traceId,
                                  String agentName, String inputText, String constraintsJson,
                                  String nodeKey, String agentType, String inputVersion) {
        AgentRun run = create(userId, conversationId, planId, traceId, agentName, inputText, constraintsJson);
        run.setNodeKey(nodeKey);
        run.setAgentType(agentType);
        run.setInputVersion(inputVersion);
        // 节点字段随创建一起落库（短事务内）
        AgentRun patch = new AgentRun();
        patch.setId(run.getId());
        patch.setNodeKey(nodeKey);
        patch.setAgentType(agentType);
        patch.setInputVersion(inputVersion);
        patch.setUpdateTime(new Date());
        agentRunMapper.updateById(patch);
        return run;
    }


    /**
     * 原子领取：仅当状态为 PENDING/WAITING_INPUT 且 attempt 未被别人推进时成功。
     *
     * @return true=领取成功（attempt 已 +1），false=已被其他执行器领取
     */
    /**
     * 显式重试领取（R2）：允许 FAILED 或超过心跳阈值（默认 10 分钟）的 RUNNING。
     * 仍然是原子条件更新，旧 attempt 的写回会被拒绝。
     */
    public boolean claimForRetry(Long runId, int expectedAttempt) {
        Boolean claimed = transactionTemplate.execute(status -> {
            Date staleBefore = new Date(System.currentTimeMillis() - STALE_RUNNING_MILLIS);
            int rows = agentRunMapper.update(null, new UpdateWrapper<AgentRun>()
                    .eq("id", runId)
                    .eq("attempt", expectedAttempt)
                    .and(wrapper -> wrapper
                            .eq("status", AgentRunStatus.FAILED.name())
                            .or()
                            .eq("status", AgentRunStatus.RUNNING.name())
                            .lt("update_time", staleBefore))
                    .set("status", AgentRunStatus.RUNNING.name())
                    .setSql("attempt = attempt + 1")
                    .setSql("version = version + 1")
                    .set("update_time", new Date()));
            return rows > 0;
        });
        return Boolean.TRUE.equals(claimed);
    }

    public boolean claim(Long runId, int expectedAttempt) {
        Boolean claimed = transactionTemplate.execute(status -> {
            AgentRun update = new AgentRun();
            update.setUpdateTime(new Date());
            int rows = agentRunMapper.update(update, new UpdateWrapper<AgentRun>()
                    .eq("id", runId)
                    .eq("attempt", expectedAttempt)
                    .in("status", List.of(AgentRunStatus.PENDING.name(), AgentRunStatus.WAITING_INPUT.name()))
                    .set("status", AgentRunStatus.RUNNING.name())
                    .setSql("attempt = attempt + 1")
                    .setSql("version = version + 1")
                    .set("update_time", new Date()));
            return rows > 0;
        });
        return Boolean.TRUE.equals(claimed);
    }

    /**
     * 写回终态/等待态（持有权校验：attempt 必须仍是本次执行的）。
     */
    public boolean finish(Long runId,
                          int attempt,
                          AgentRunStatus status,
                          String resultJson,
                          String messagesJson,
                          String errorMsg) {
        Boolean finished = transactionTemplate.execute(txStatus -> {
            int rows = agentRunMapper.update(null, new UpdateWrapper<AgentRun>()
                    .eq("id", runId)
                    .eq("attempt", attempt)
                    .set("status", status.name())
                    .set("result_json", resultJson)
                    .set("messages_json", messagesJson)
                    .set("error_msg", errorMsg)
                    .setSql("version = version + 1")
                    .set("update_time", new Date()));
            return rows > 0;
        });
        boolean ok = Boolean.TRUE.equals(finished);
        if (!ok) {
            log.warn("run 写回被持有权校验拒绝（可能已被新执行器领取），runId={}, attempt={}", runId, attempt);
        }
        return ok;
    }

    /**
     * 保存消息序列（检查点，持有权校验）。恢复时必须用完整消息，不能用摘要。
     */
    public boolean saveMessages(Long runId, int attempt, String messagesJson) {
        Boolean saved = transactionTemplate.execute(txStatus -> {
            int rows = agentRunMapper.update(null, new UpdateWrapper<AgentRun>()
                    .eq("id", runId)
                    .eq("attempt", attempt)
                    .set("messages_json", messagesJson)
                    .setSql("version = version + 1")
                    .set("update_time", new Date()));
            return rows > 0;
        });
        return Boolean.TRUE.equals(saved);
    }

    /**
     * 按会话查找最近一个“待续跑/执行中”的 run（R2：首次请求没有 planId 时的续跑通道）。
     * 只返回该用户自己的任务。
     */
    public AgentRun findLatestResumableByConversation(Long conversationId, Long userId) {
        if (conversationId == null || userId == null) {
            return null;
        }
        return agentRunMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AgentRun>()
                .eq("conversation_id", conversationId)
                .eq("user_id", userId)
                .in("status", List.of(AgentRunStatus.WAITING_INPUT.name(), AgentRunStatus.RUNNING.name(),
                        AgentRunStatus.PENDING.name()))
                .eq("is_deleted", NOT_DELETED)
                .orderByDesc("id")
                .last("limit 1"));
    }

    /** 列出计划下所有子 run（状态快照用）。 */
    public List<AgentRun> listByPlan(Long planId) {
        if (planId == null) {
            return List.of();
        }
        return agentRunMapper.selectList(new QueryWrapper<AgentRun>()
                .eq("plan_id", planId)
                .eq("is_deleted", NOT_DELETED)
                .orderByAsc("id"));
    }


    /**
     * 查找该计划下最近一次 run（补参/重试路由用）。
     */
    public AgentRun findLatestByPlan(Long planId) {
        if (planId == null) {
            return null;
        }
        return agentRunMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AgentRun>()
                .eq("plan_id", planId)
                .eq("is_deleted", NOT_DELETED)
                .orderByDesc("id")
                .last("limit 1"));
    }
}
