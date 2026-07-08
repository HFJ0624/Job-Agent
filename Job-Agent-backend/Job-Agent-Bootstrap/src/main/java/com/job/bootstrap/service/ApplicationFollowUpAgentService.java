package com.job.bootstrap.service;

import com.job.common.entity.application.JobApplicationRecord;

/**
 * 求职跟进 Agent 服务。
 *
 * <p>核心职责：承接“求职进度发生变化后，系统应自动执行什么动作”的业务规则，通过事件驱动方式触发自动化跟进流程（如面试提醒、邮件通知、任务创建等）。</p>
 *
 * <p>所属业务模块：求职跟进 / 事件驱动自动化</p>
 *
 * <p>主要调用链：领域事件监听器 / 定时任务 → ApplicationFollowUpAgentService → 跟进规则引擎 / 邮件服务 / 任务调度服务</p>
 */
public interface ApplicationFollowUpAgentService {

    /**
     * 当求职记录进入面试阶段时触发自动跟进流程。
     * <p>当前实现包括：创建面试准备提醒事项，并投递一条异步邮件通知任务。</p>
     *
     * @param application 已保存的求职记录快照（包含岗位、公司、面试时间等上下文信息）
     */
    void onInterviewScheduled(JobApplicationRecord application);
}
