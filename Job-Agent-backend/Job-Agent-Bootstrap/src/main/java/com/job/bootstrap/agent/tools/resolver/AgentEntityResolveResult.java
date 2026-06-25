package com.job.bootstrap.agent.tools.resolver;

import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 作者: hfj
 * 功能: Agent 工具实体解析结果
 * 日期: 2026/6/25
 */
@Data
@Builder
public class AgentEntityResolveResult {

    /**
     * 是否需要用户进一步确认。
     */
    private boolean needClarification;

    /**
     * 给用户看的确认提示。
     */
    private String message;

    /**
     * 已解析到的简历。
     */
    private JobResume resume;

    /**
     * 已解析到的岗位。
     */
    private JobPosition job;

    /**
     * 岗位重名或模糊命中多条时，返回给用户选择的候选项。
     */
    private List<Map<String, Object>> candidates;
}
