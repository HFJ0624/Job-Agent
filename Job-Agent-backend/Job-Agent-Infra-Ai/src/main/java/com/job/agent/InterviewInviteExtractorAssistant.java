package com.job.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 作者: hfj
 * 功能: 面试邀约信息提取 AI Service
 *
 * 设计说明:
 * 1. 这个 Assistant 只负责从 HR 回复中提取结构化面试信息。
 * 2. 不负责生成回复话术。
 * 3. 不负责修改数据库状态。
 * 4. 状态更新由业务 Service 控制。
 */
public interface InterviewInviteExtractorAssistant {

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
