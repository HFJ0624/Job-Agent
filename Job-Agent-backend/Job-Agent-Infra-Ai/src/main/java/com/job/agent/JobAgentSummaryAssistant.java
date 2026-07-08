package com.job.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Agent 执行结果总结助手 AI Service 接口。
 *
 * <p>核心职责：
 * 在后端 Executor 按 Plan 顺序执行完所有工具步骤后，把结构化的执行结果（计划、步骤状态、工具返回 JSON、
 * 失败原因）整理成自然、分点的中文回复，供普通用户阅读。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的 LangChain4j Assistant 接口层。</p>
 *
 * <p>主要调用链：
 * AgentChatService.chat -> AgentPlanExecutorService.executePlan -> AiModelGatewayService.chat
 * -> (AGENT_SUMMARY 场景) -> JobAgentSummaryAssistant.summarize
 * 当数据库网关不可用时，业务层降级为 buildDeterministicExecutorAnswer，不调用本接口。</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>输入由 AgentChatServiceImpl.buildExecutorSummaryMessage 组装，包含用户输入、长期记忆、计划、执行结果；</li>
 *   <li>输出经过 AgentGuardrailService.sanitizeFinalAnswer 二次脱敏后返回前端；</li>
 *   <li>本接口只做“总结”阶段，不参与 Planning、Tool Calling、Observation 等阶段。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 这是一个纯总结型 Assistant，禁止再次调用任何工具。
 * 2. 不得编造工具结果里没有出现的事实，避免给用户造成虚假承诺。
 * 3. 失败步骤必须明确告知用户失败原因和下一步补充方式。</p>
 *
 * 作者:hfj
 * 日期:2026/6/20
 */
public interface JobAgentSummaryAssistant {

    /**
     * 把 Executor 已经完成的计划步骤和工具结果总结成中文回复。
     *
     * <p>核心处理流程：
     * 1. 框架将 SystemMessage 作为系统提示词注入模型请求；
     * 2. 调用方传入的 message 包含【当前用户输入】【长期记忆上下文】【已召回的长期记忆】【计划原始目标】
     *    【后端执行计划】【Executor 执行结果】【总结要求】等结构化分段；
     * 3. 模型只依据上述事实生成中文回复，不调用工具、不编造信息；
     * 4. 框架返回模型文本，调用方再做一次 Guardrails 脱敏。</p>
     *
     * @param message 已组装好的执行结果上下文，包含计划、步骤、工具结果、长期记忆等
     * @return 面向用户的中文执行结果总结，分点呈现成功步骤、失败原因和下一步建议
     */
    @SystemMessage("""
            你是 Job-Agent 执行结果总结助手。

            你的职责：
            1. 只根据后端 Agent Executor 已经执行完成的计划步骤和工具结果，整理成中文回复。
            2. 不要再次调用任何工具，也不要要求调用工具。
            3. 不要编造工具结果里没有出现的信息。
            4. 如果某个步骤失败，要明确说明失败原因和用户下一步可以怎么补充。
            5. 输出要清晰、自然、分点，像一个求职助手在向用户汇报执行结果。

            注意：
            - 输入中可能包含 JSON、计划步骤、工具名、状态和错误信息。
            - 普通用户不需要看到原始 JSON 字段名，除非字段本身就是业务上必要的 ID。
            """)
    String summarize(@UserMessage String message);
}
