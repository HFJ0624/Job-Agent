package com.job.bootstrap.agent.tools;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.JobPositionService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.position.JobPosition;
import com.job.exception.AgentToolException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:岗位搜索工具
 * 使用场景:
 * 1. 用户说“帮我找 Java 后端岗位”
 * 2. 用户说“推荐几个上海的后端岗位”
 * 3. 用户说“有没有 Spring Boot 相关岗位”
 * 设计说明:
 * 1. 第一版只查已发布岗位。
 * 2. 只返回前 5 条，避免工具输出过长。
 * 3. 返回字段要控制，避免把数据库里的冗余字段全部暴露给大模型。
 * 日期: 2026/6/8 15:15
 */
@Component
@RequiredArgsConstructor
public class JobSearchTool {

    private static final String TOOL_NAME = "JobSearchTool.searchJobs";

    private final JobPositionService jobPositionService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;
    private final AgentToolGuard agentToolGuard;

    /**
     * 搜索岗位。
     *
     * @param keyword 搜索关键词，例如 Java、Spring Boot、后端开发
     * @param city 城市，可为空
     * @param educationReq 学历要求，可为空
     * @param experienceReq 经验要求，可为空
     * @return 岗位列表 JSON
     */
    @Tool("""
            根据关键词、城市、学历、经验等条件搜索已发布岗位。
            当用户要求“找岗位”“搜索岗位”“推荐岗位”“有哪些岗位”时使用本工具。
            """)
    public String searchJobs(
            @P("岗位关键词，例如 Java、Spring Boot、后端开发，可以为空") String keyword,
            @P("工作城市，例如 北京、上海、杭州，可以为空") String city,
            @P("学历要求，例如 本科、硕士、不限，可以为空") String educationReq,
            @P("经验要求，例如 1-3年、3-5年、应届生，可以为空") String experienceReq
    ) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("keyword", keyword);
        input.put("city", city);
        input.put("educationReq", educationReq);
        input.put("experienceReq", experienceReq);

        AgentToolSchema schema = null;
        try {
            schema = agentToolGuard.validate(TOOL_NAME, input);
            Map<String, Object> traceInput = agentToolGuard.buildTraceInput(TOOL_NAME, schema, input);

            /*
             * 调用已有岗位分页查询能力。
             * pageNo=1, pageSize=5:
             * 只给 Agent 返回少量候选岗位，避免一次上下文太长。
             */
            IPage<JobPosition> page = jobPositionService.pagePositions(
                    1L,
                    5L,
                    normalize(keyword),
                    null,
                    normalize(city),
                    null,
                    null,
                    normalize(educationReq),
                    normalize(experienceReq),
                    null,
                    null,
                    true
            );

            /*
             * 只挑选模型真正需要的字段返回。
             * 不建议直接返回完整实体，避免字段过多、token 浪费。
             */
            List<Map<String, Object>> jobs = page.getRecords().stream()
                    .map(this::toSimpleJob)
                    .toList();

            Map<String, Object> output = Map.of(
                    "total", page.getTotal(),
                    "jobs", jobs
            );

            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    TOOL_NAME,
                    traceInput,
                    output,
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            //失败也记录
            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    TOOL_NAME,
                    agentToolGuard.buildTraceInput(TOOL_NAME, schema, input),
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            if (e instanceof AgentToolException toolException) {
                throw toolException;
            }
            throw new RuntimeException("岗位搜索工具调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将岗位实体转成简洁结构。
     */
    private Map<String, Object> toSimpleJob(JobPosition job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jobId", job.getId());
        map.put("jobTitle", job.getJobTitle());
        map.put("city", job.getCity());
        map.put("district", job.getDistrict());
        map.put("educationReq", job.getEducationReq());
        map.put("experienceReq", job.getExperienceReq());
        map.put("minSalary", job.getMinSalary());
        map.put("maxSalary", job.getMaxSalary());
        map.put("skillKeywords", job.getSkillKeywords());
        return map;
    }

    /**
     * 空字符串转 null。
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
