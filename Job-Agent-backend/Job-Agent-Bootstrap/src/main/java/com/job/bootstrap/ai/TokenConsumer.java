package com.job.bootstrap.ai;

/**
 * 功能: 模型流式输出的 token 消费者（任务 G5）。
 *
 * 说明:
 * 1. 网关流式调用时，每解析出一个增量 token 就回调 onToken；
 * 2. 实现方（如 SSE 控制器）负责把 token 推给前端，需自行处理客户端断开异常；
 * 3. 阻塞降级时，整段文本会作为一个 token 一次性回调，实现方无需区分两种模式。
 */
@FunctionalInterface
public interface TokenConsumer {

    /**
     * 消费一个增量 token（降级时为整段文本）。
     *
     * @param token 增量文本
     */
    void onToken(String token);
}
