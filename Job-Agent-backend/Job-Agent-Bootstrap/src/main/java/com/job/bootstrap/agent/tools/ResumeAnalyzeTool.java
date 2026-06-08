package com.job.bootstrap.agent.tools;

import com.job.bootstrap.service.JobResumeScoreService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.vo.resume.ResumeScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
/**
 * 作者:hfj
 * 功能:简历分析工具
 * 说明:
 * 1. Agent 不直接操作数据库。
 * 2. Agent 调用 Tool，Tool 再调用业务 Service。
 * 3. 这样方便测试、限权、记录日志和后续扩展。
 * 日期: 2026/6/8 15:12
 */
@Component
@RequiredArgsConstructor
public class ResumeAnalyzeTool {

    private final JobResumeScoreService jobResumeScoreService;
    private final ObjectMapper objectMapper;

    @Tool("根据用户ID、简历ID和目标岗位，对简历进行整体评分，返回总分、维度分、优势、问题和优化建议")
    public String analyzeResume(
            @P("当前登录用户ID") Long userId,
            @P("简历ID") Long resumeId,
            @P("目标岗位名称，可以为空，例如 Java后端开发") String targetPosition
    ) {
        try {
            ResumeScoreVO result = jobResumeScoreService.scoreResume(userId, resumeId, targetPosition);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "简历分析失败：" + e.getMessage();
        }
    }
}
