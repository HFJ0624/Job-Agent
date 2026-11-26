package com.job.bootstrap.config.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.List;

/**
 * 功能: 过滤定时任务线程的 SQL 日志（只用于控制台的 SQL appender）。
 *
 * 背景:
 * 1. WorkflowTaskScheduler 每 30 秒轮询 agent_workflow_task，会持续打印
 *    "Preparing: SELECT ... WHERE status IN (...)"，把控制台刷满；
 * 2. 开发时又希望看到**业务请求**执行的 SQL（HTTP/SSE 线程）。
 *
 * 做法: 按线程名前缀丢弃定时任务线程事件（Spring 调度线程为 scheduling-*），
 * 其他线程的事件放行（NEUTRAL），不影响日志级别判断。
 * 说明: 只作用于 SQL_CONSOLE，业务 INFO 日志和文件日志不受影响。
 */
public class SchedulerLogFilter extends Filter<ILoggingEvent> {

    /** 定时任务/公共线程池前缀：这些线程的 SQL 不进控制台。 */
    private static final List<String> SCHEDULER_PREFIXES = List.of(
            "scheduling-",
            "task-scheduler-",
            "Timer-"
    );

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null) {
            return FilterReply.NEUTRAL;
        }
        String threadName = event.getThreadName();
        if (threadName == null) {
            return FilterReply.NEUTRAL;
        }
        for (String prefix : SCHEDULER_PREFIXES) {
            if (threadName.startsWith(prefix)) {
                return FilterReply.DENY;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
