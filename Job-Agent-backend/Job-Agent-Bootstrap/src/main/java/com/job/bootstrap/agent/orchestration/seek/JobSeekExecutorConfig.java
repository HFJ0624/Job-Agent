package com.job.bootstrap.agent.orchestration.seek;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 功能: 复合任务并行节点执行池（第二阶段）。
 *
 * 设计说明:
 * 1. 只有“依赖确实独立”的节点（简历画像 ∥ 岗位搜索）才并行；
 * 2. Spring 管理的**有界**线程池（核心 2、最大 2、队列 8），拒绝策略用 Abort 让调用方降级为串行——
 *    不用公共 ForkJoinPool，也不创建无界线程；
 * 3. 每个任务独立上下文，提交前显式传递可信身份，线程内自行设置/清理 ThreadLocal 钩子。
 */
@Configuration
public class JobSeekExecutorConfig {

    public static final String EXECUTOR_BEAN = "jobSeekNodeExecutor";

    @Bean(name = EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor jobSeekNodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("job-seek-node-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
