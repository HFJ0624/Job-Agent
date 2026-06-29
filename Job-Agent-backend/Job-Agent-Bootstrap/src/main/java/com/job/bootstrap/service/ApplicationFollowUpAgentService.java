package com.job.bootstrap.service;

import com.job.common.entity.application.JobApplicationRecord;

/**
 * 功能：求职跟进 Agent 服务。
 *
 * 说明：
 * 1. 它承接“求职进度发生变化后，系统应该自动做什么”的业务规则。
 * 2. 第一版先实现面试场景：创建面试准备提醒，并投递一条异步邮件通知任务。
 */
public interface ApplicationFollowUpAgentService {

    /**
     * 当求职记录进入面试中时触发自动跟进。
     *
     * @param application 已保存的求职记录快照
     */
    void onInterviewScheduled(JobApplicationRecord application);
}
