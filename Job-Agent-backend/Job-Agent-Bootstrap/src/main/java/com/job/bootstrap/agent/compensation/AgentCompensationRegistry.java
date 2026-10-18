package com.job.bootstrap.agent.compensation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 功能: 补偿动作注册表（任务 G9）。
 *
 * 设计说明:
 * 1. Spring 把所有 AgentCompensationAction 实现 bean 注入进来，按 toolName 建索引——
 *    新增可补偿工具 = 新增一个 @Component，执行器和注册表零改动；
 * 2. 查询不到 = 该工具的副作用不需要补偿（只读工具根本不该走到补偿；未注册的写工具
 *    由 AgentPlanCompensator 按 NO_ACTION 记事件，保证“每个副作用步骤都有明确交代”；
 * 3. 索引不可变（collect 后 toMap + copy），运行期不会出现半注册状态。
 */
@Slf4j
@Component
public class AgentCompensationRegistry {

    private final Map<String, AgentCompensationAction> actions;

    public AgentCompensationRegistry(List<AgentCompensationAction> actionBeans) {
        this.actions = actionBeans.stream()
                .collect(Collectors.toMap(AgentCompensationAction::toolName, Function.identity(), (first, second) -> {
                    log.warn("补偿动作重复注册，保留前者，toolName={}", first.toolName());
                    return first;
                }));
        log.info("补偿动作注册完成，共 {} 个：{}", this.actions.size(), this.actions.keySet());
    }

    /**
     * 查询工具的补偿动作。
     *
     * @param toolName 工具名
     * @return 补偿动作；未注册返回 empty（调用方按 NO_ACTION 处理）
     */
    public Optional<AgentCompensationAction> find(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(actions.get(toolName.trim()));
    }
}
