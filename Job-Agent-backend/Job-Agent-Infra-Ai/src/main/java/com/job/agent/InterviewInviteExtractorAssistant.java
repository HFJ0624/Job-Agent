package com.job.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 面试邀约信息提取 AI Service 接口。
 *
 * <p>核心职责：
 * 从 HR 回复文本中判断是否存在面试邀约，并抽取结构化面试信息（时间、地点、方式、会议链接、联系人等），
 * 输出严格 JSON，供后端 Service 解析并更新求职进度状态。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的 LangChain4j Assistant 接口层。</p>
 *
 * <p>主要调用链：
 * HrReplyRecognitionService.recognize -> InterviewInviteExtractorAssistant.extract
 * -> Jackson 解析 JSON -> 更新 hr_reply_recognition_record / job_application_record 状态
 * 本接口不参与 Agent 主链路的 Planning / Tool Calling，只服务于 HR 回复识别场景。</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>由 InterviewInviteExtractorConfig 注入共享 ChatModel；</li>
 *   <li>输出固定 JSON Schema，业务层用 Jackson 解析；</li>
 *   <li>不负责生成回复话术（由 HrCommunicationAssistant 负责）；</li>
 *   <li>不负责修改数据库状态，状态更新由 HrReplyRecognitionService 控制。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 这个 Assistant 只负责从 HR 回复中提取结构化面试信息。
 * 2. 不负责生成回复话术，不负责修改数据库状态。
 * 3. 状态更新由业务 Service 控制，保证模型输出与状态变更解耦。</p>
 *
 * 作者: hfj
 */
public interface InterviewInviteExtractorAssistant {

    /**
     * 从 HR 回复文本中抽取结构化面试邀约信息。
     *
     * <p>核心处理流程：
     * 1. 框架将 SystemMessage 注入为系统提示词，约束模型只输出 JSON；
     * 2. 调用方在 prompt 中拼好 HR 原文、历史沟通上下文；
     * 3. 模型判断是否存在面试邀约，并尝试抽取时间、方式、地点、会议链接等字段；
     * 4. 模型按固定 JSON Schema 输出，confidence 反映自身把握度；
     * 5. 调用方用 Jackson 解析 JSON，写入 hr_reply_recognition_record。</p>
     *
     * @param prompt 已组装的 Prompt，包含 HR 原文和必要的沟通上下文
     * @return 严格 JSON 字符串，字段定义见 SystemMessage 中的 Schema 示例；解析失败时由调用方降级处理
     */
    @SystemMessage("""
            你是一个招聘沟通信息抽取助手。
            你的任务是从 HR 回复文本中判断是否存在面试邀约，并抽取面试时间、地点、方式、会议链接等信息。

            输出要求：
            1. 必须只输出 JSON，不要输出 Markdown，不要输出解释文本。
            2. 如果无法判断某个字段，字段值使用 null。
            3. interviewInvited 表示是否存在面试邀约或面试时间协商。
            4. interviewTime 必须尽量转换成 yyyy-MM-dd HH:mm:ss 格式。
            5. 如果 HR 只是询问“明天下午方便吗”，needUserConfirm 要为 true。
            6. 如果 HR 已经明确给出时间、方式、地点，needUserConfirm 可以为 false。
            7. interviewMethod 只能是 ONLINE、OFFLINE、PHONE、UNKNOWN。
            8. confidence 是 0 到 100 的数字。

            JSON 格式如下：
            {
              "interviewInvited": true,
              "interviewTime": "2026-06-10 15:00:00",
              "dateText": "明天",
              "timeText": "下午3点",
              "interviewMethod": "ONLINE",
              "interviewMethodDesc": "线上面试",
              "interviewLocation": "腾讯会议",
              "interviewPlatform": "腾讯会议",
              "meetingLink": null,
              "interviewContact": null,
              "needUserConfirm": true,
              "confirmQuestion": "请确认是否方便明天下午3点线上面试，并询问会议链接。",
              "confidence": 88,
              "reason": "识别到HR询问明天下午3点是否方便线上面试。"
            }
            """)
    String extract(@UserMessage String prompt);
}
