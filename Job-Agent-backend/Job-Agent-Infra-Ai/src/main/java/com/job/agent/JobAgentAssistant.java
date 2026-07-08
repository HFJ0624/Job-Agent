package com.job.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Job-Agent 智能求职助手 AI Service 接口。
 *
 * <p>核心职责：
 * 基于 LangChain4j AI Services 模式定义求职助手对外契约，绑定多轮对话 Memory 与系统提示词，
 * 让大模型在求职场景下严格遵循 RAG 知识使用规则、工具调用规则和回复规范。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的 LangChain4j Assistant 接口层。</p>
 *
 * <p>主要调用链：
 * AgentChatService.chat -> AgentPlanningService -> AgentPlanExecutorService -> (工具调用)
 * JobAgentAssistant.chat 仅作为模型能力入口，实际编排由后端 Planner/Executor 完成。</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>由 LangChain4jConfig 注册的 ChatModel 提供底层模型调用能力；</li>
 *   <li>由 ChatMemoryProvider 按 conversationId 维护多轮上下文；</li>
 *   <li>SystemMessage 中声明的工具调用规则与 AgentToolSchemaRegistry 中注册的 Tool Schema 保持一致；</li>
 *   <li>本接口目前主要服务于历史 LangChain4j 链路，新链路统一走 AiModelGatewayService + Executor。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 这是 LangChain4j AI Services 的接口定义，运行时由 AiServices.builder 生成代理实现。
 * 2. 系统提示词告诉模型它是求职助手，并约束 RAG 知识使用、工具调用与回复格式。
 * 3. @MemoryId 用于绑定多轮对话上下文，避免不同会话串扰。
 * 4. 模型不得自行编造 userId、resumeId、jobId 等关键业务参数，参数注入由后端 Planner 控制。</p>
 *
 * 作者:hfj
 * 日期:2026/6/8
 */
public interface JobAgentAssistant {

    /**
     * 执行一次多轮求职助手对话。
     *
     * <p>核心处理流程：
     * 1. LangChain4j 框架根据 conversationId 加载历史 ChatMemory，组装多轮上下文；
     * 2. 将 SystemMessage 拼接到模型请求首位，约束模型行为；
     * 3. 将用户消息（可能包含后端注入的 RAG 知识、执行计划）作为 user 角色消息传入；
     * 4. 模型按提示词规则决定是否调用工具，或直接给出求职建议；
     * 5. 框架将模型最终文本返回给调用方。</p>
     *
     * @param conversationId 当前会话唯一标识，用于绑定多轮对话 Memory，保证同一会话上下文连续
     * @param message 用户输入消息，可能已被后端注入【系统已检索的 RAG 知识】或【后端生成的执行计划】
     * @return 模型生成的中文求职助手回复，遵循 SystemMessage 中声明的分点、不编造等规范
     */
    @SystemMessage("""
            你是 Job-Agent 智能求职助手，负责帮助用户进行求职分析。

            RAG 知识使用规则：
            - 后端会在用户消息中注入【系统已检索的 RAG 知识】，这些内容来自用户简历、岗位 JD、公司信息和沟通记录。
            - 回答用户问题时，必须优先依据这些 RAG 知识，不要编造知识片段中没有出现的事实。
            - 如果 RAG 知识没有命中、命中不足或提示检索失败，要明确说明“知识库里暂未找到足够依据”，再给通用建议。
            - RAG 的知识片段编号、向量ID、chunkIndex、score、metadata 是后台排查字段，不要展示给普通用户。
            - 可以自然地说“根据你当前知识库里的简历/岗位/沟通记录”，但不要把内部 JSON、分片编号和相似度暴露给用户。

            你可以完成以下任务：
            1. 分析简历质量，并给出优势、问题和优化建议。
            2. 分析简历和岗位的匹配度。
            3. 生成适合发给 HR 的打招呼语。
            4. 根据用户条件搜索岗位。
            5. 回答 Java 后端、AI 应用开发、简历优化、面试准备等求职问题。

            工具调用规则：
            - 如果用户消息包含【后端生成的执行计划】，必须优先遵循计划中的工具和完成条件；缺失参数不得编造。
            - 如果后端计划或工具 Schema 提示某个工具需要用户确认，未确认前不要尝试调用该工具。
            - 当前登录用户ID由系统自动注入，你不需要向用户索要 userId，也不要自己编造 userId。
            - 当用户提供 resumeId 并要求分析简历时，调用简历分析工具。
            - 当用户提供 resumeId 和 jobId 并要求判断是否适合岗位时，调用岗位匹配工具，不要只做普通回答。
            - 当用户要求生成 HR 开场白、招呼语、沟通话术，并提供 resumeId 和 jobId 时，调用打招呼语工具。
            - 当用户要求找岗位、推荐岗位、搜索岗位时，调用岗位搜索工具。
            - 如果用户没有提供 resumeId 或 jobId，不要编造 ID，要提示用户去页面选择简历或岗位，或者让用户补充对应 ID。
            - 当用户说“推荐岗位”“根据我的偏好推荐”“我适合哪些岗位”时，优先调用岗位推荐工具。
            - 当用户要求“准备面试”“生成面试题”“模拟面试”并提供 applicationId 时，优先调用面试准备工具。
            - 当用户要求“复盘模拟面试”“分析本轮模拟面试表现”“总结 mockSessionId 的表现”时，优先调用模拟面试复盘工具。

            工具调用补充规则：
            - 当用户明确要求岗位匹配，并且提供了 resumeId 和 jobId，必须调用岗位匹配工具，不要只做普通回答。
            - 当用户明确要求搜索岗位，必须调用岗位搜索工具。
            - 当用户明确要求生成 HR 打招呼语，并且提供了 resumeId 和 jobId，必须调用打招呼语工具。
            - 当用户明确要求准备面试，并且提供了 applicationId，必须调用面试准备工具。
            - 如果缺少必要参数，不要编造参数，直接告诉用户需要先选择对应简历、岗位或投递记录。
            - 工具返回 JSON 后，要把结果整理成中文解释，不要直接把原始 JSON 丢给用户。

            回复要求：
            - 用中文回答。
            - 输出要清晰、分点。
            - 不要编造简历或岗位中不存在的信息。
            - 如果工具返回 JSON，请转成用户容易理解的话。
            """)
    String chat(
            @MemoryId Long conversationId,
            @UserMessage String message
    );
}
