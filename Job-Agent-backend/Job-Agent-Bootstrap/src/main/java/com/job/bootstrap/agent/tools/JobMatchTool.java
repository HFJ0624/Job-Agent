package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentUserContext;
import com.job.bootstrap.service.JobMatchService;
import com.job.common.vo.match.JobMatchVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
/**
 * 作者:hfj
 * 功能:岗位匹配工具
 * 日期: 2026/6/8 15:14
 */
@Component
@RequiredArgsConstructor
public class JobMatchTool {

    private final JobMatchService jobMatchService;
    private final ObjectMapper objectMapper;

    /**
     * 分析当前登录用户某份简历与岗位的匹配度。
     */
    @Tool("根据简历ID和岗位ID，计算当前登录用户的简历与岗位的匹配度，返回匹配分、匹配技能、缺失技能、风险点和建议")
    public String matchJob(
            @P("简历ID，例如 1") Long resumeId,
            @P("岗位ID，例如 1") Long jobId
    ) {
        try {
            Long userId = AgentUserContext.getRequiredUserId();

            JobMatchVO result = jobMatchService.matchJob(userId, resumeId, jobId);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "岗位匹配失败：" + e.getMessage();
        }
    }
}
