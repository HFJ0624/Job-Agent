package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.JobGreetingService;
import com.job.common.vo.greeting.GreetingVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
/**
 * 作者:hfj
 * 功能:HR 打招呼语生成工具
 * 日期: 2026/6/8 15:14
 */
@Component
@RequiredArgsConstructor
public class GreetingGenerateTool {

    private final JobGreetingService jobGreetingService;
    private final ObjectMapper objectMapper;

    @Tool("根据用户ID、简历ID、岗位ID和语气风格，生成适合发给HR的打招呼语")
    public String generateGreeting(
            @P("当前登录用户ID") Long userId,
            @P("简历ID") Long resumeId,
            @P("岗位ID") Long jobId,
            @P("语气风格，例如 自然、正式、自信、实习生风格、社招风格、简洁直达") String style
    ) {
        try {
            GreetingVO result = jobGreetingService.generateGreeting(userId, resumeId, jobId, style);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "打招呼语生成失败：" + e.getMessage();
        }
    }
}
