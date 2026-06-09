package com.job.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 作者: hfj
 * 功能: HR 沟通回复生成 AI Service
 * 设计说明:
 * 1. 这个 Assistant 专门负责根据 HR 回复生成建议回复。
 * 2. 不参与工具调用。
 * 3. 不负责状态流转。
 * 4. 状态流转由用户手动选择，后端保存。
 */
public interface HrCommunicationAssistant {

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
