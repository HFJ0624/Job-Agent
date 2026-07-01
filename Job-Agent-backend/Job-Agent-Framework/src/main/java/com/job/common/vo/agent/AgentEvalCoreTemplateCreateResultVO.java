package com.job.common.vo.agent;

import com.job.common.entity.agent.AgentEvalCase;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能:Agent Eval 核心链路模板创建结果。
 *
 * 设计说明:
 * 1. createdCount 告诉前端本次真正新增了几条用例。
 * 2. skippedTypes 告诉前端哪些核心类型因为已存在而被跳过。
 * 3. createdCases 主要给后端测试和后续扩展使用，Controller 返回时也可用于页面提示。
 */
@Data
public class AgentEvalCoreTemplateCreateResultVO {
    private Integer createdCount = 0;
    private Integer skippedCount = 0;
    private List<String> skippedTypes = new ArrayList<>();
    private List<AgentEvalCase> createdCases = new ArrayList<>();
}
