package com.job.bootstrap.agent.context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能: Agent 上下文构建（Context Builder）配置属性（任务6）。
 *
 * 说明:
 * 1. 用字符数近似 token 预算（与 AgentMemoryContextServiceImpl 一致，按 2 字符/token 估算），
 *    避免引入额外分词依赖。
 * 2. historyMaxChars / executionPreviewChars 近似路线图的预算分配
 *    （历史 30% / 记忆 20% / 执行结果 40% / 其他 10%）：记忆段由 AgentMemoryContextServiceImpl
 *    自行按 1600 字符封顶，这里负责历史段与执行结果预览段。
 */
@Data
@ConfigurationProperties(prefix = "job.agent.context")
public class AgentContextProperties {

    /**
     * 注入总结提示词的最近对话轮数（1 轮 = 1 问 1 答）。
     */
    private int historyRounds = 6;

    /**
     * 单条历史消息的最大字符数，超长消息截断，防止一条刷屏挤占预算。
     */
    private int perMessageMaxChars = 400;

    /**
     * 多轮历史整体的字符预算（约 30% 总预算），从旧到新加入，超预算停止并标记截断。
     */
    private int historyMaxChars = 2400;

    /**
     * 滚动摘要的最大字符数。
     */
    private int summaryMaxChars = 600;

    /**
     * Executor 执行结果预览的最大字符数（原为硬编码 1000，现可配置）。
     */
    private int executionPreviewChars = 1000;

    /**
     * 触发滚动摘要的“窗口外新旧消息”数量阈值，少于该值不重复压缩，控制成本。
     */
    private int overflowThreshold = 4;

    /**
     * 每次扫描“窗口外待压缩”消息的最大条数，防止一次压缩过量。
     */
    private int overflowScanLimit = 20;
}
