package com.job.bootstrap.agent.compensation;

import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;

/**
 * 功能: 不可逆外部动作的补偿占位（任务 G9）。
 *
 * 说明:
 * 1. 邮件、通知这类动作一旦发出就触达外部世界，没有“反向操作”可做；
 * 2. 这里显式注册为 IRREVERSIBLE：补偿器会记录事件并让上层如实告知用户，
 *    而不是静默跳过——“不可逆”必须是一个被记录的决策，不是一个秘密；
 * 3. compensate 是 no-op：留给未来做“发一封澄清邮件”之类的补救动作。
 */
public abstract class IrreversibleExternalCompensation implements AgentCompensationAction {

    @Override
    public CompensationKind kind() {
        return CompensationKind.IRREVERSIBLE;
    }

    @Override
    public void compensate(Long userId, Long planId, AgentPlanStepExecutionResult stepResult) {
        // 无自动反向操作，事实由观测事件与用户告知承载
    }
}
