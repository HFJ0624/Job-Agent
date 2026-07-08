package com.job.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * HR 沟通回复生成 AI Service 接口。
 *
 * <p>核心职责：
 * 根据用户提供的 HR 回复上下文（含公司、岗位、HR 文本、用户期望语气等），
 * 生成一段自然、礼貌、可直接发给 HR 的回复正文。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的 LangChain4j Assistant 接口层。</p>
 *
 * <p>主要调用链：
 * 前端 -> JobCommunicationRecordService -> HrCommunicationAssistant.generateReply
 * 本接口不参与 Agent 主链路的 Planning / Tool Calling / Observation，只服务于沟通话术生成场景。</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>由 HrCommunicationAssistantConfig 注入共享 ChatModel；</li>
 *   <li>调用方负责拼装 Prompt 并保存到 job_communication_message；</li>
 *   <li>不负责状态流转（如已读、约面试等），状态流转由用户手动选择，后端 Service 落库。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 这个 Assistant 专门负责根据 HR 回复生成建议回复，不参与工具调用。
 * 2. 不负责状态流转，状态流转由用户手动选择，后端保存。
 * 3. 输出只包含给 HR 的回复正文，不包含解释性文字。</p>
 *
 * 作者: hfj
 */
public interface HrCommunicationAssistant {

    /**
     * 根据 HR 回复上下文生成求职者给 HR 的回复正文。
     *
     * <p>核心处理流程：
     * 1. 框架将 SystemMessage 注入为系统提示词，约束模型只输出回复正文；
     * 2. 调用方在 prompt 中拼好 HR 原文、公司、岗位、用户期望语气等上下文；
     * 3. 模型按提示词要求生成自然、礼貌、不编造经历的中文回复；
     * 4. 调用方拿到正文后写入沟通记录表。</p>
     *
     * @param prompt 已组装的 Prompt，包含 HR 原文、用户简历要点、期望语气等上下文
     * @return 可直接发给 HR 的回复正文，不包含任何解释性文字
     */
    @SystemMessage("""
            你是一名求职沟通助手，负责帮助求职者回复 HR。

            回复要求：
            1. 回复要自然、礼貌，不要过度夸张。
            2. 不要编造用户没有提供的经历。
            3. 如果 HR 在约面试，要明确表达是否方便，并提醒用户确认时间、形式、地点。
            4. 如果 HR 询问简历或项目，要突出用户已有优势。
            5. 如果信息不充分，要给出稳妥回复，不要硬编。
            6. 输出内容只需要给 HR 的回复正文，不要输出解释。
            """)
    String generateReply(@UserMessage String prompt);
}
