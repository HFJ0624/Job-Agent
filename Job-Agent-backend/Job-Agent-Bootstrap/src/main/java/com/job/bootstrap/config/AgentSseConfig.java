package com.job.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 功能: SSE 流式对话异步执行器配置（任务 G5）。
 *
 * 设计说明:
 * 1. 流式对话期间不能占用 Tomcat 请求线程（一个连接可能挂数分钟），必须异步执行主链路；
 * 2. 有界队列 + CallerRunsPolicy：池满时由调用线程（Tomcat 线程）兜底执行，流式功能降级可用，绝不死任务；
 * 3. 单独命名 agentSseExecutor，避免与 Spring Boot 默认 taskExecutor 混淆。
 */
@Configuration
public class AgentSseConfig {

    @Bean("agentSseExecutor")
    public Executor agentSseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("agent-sse-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
