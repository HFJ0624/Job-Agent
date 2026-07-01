package com.job.common.dto.agent;

import lombok.Data;

/**
 * 功能:Agent Eval 核心链路模板用例创建参数。
 *
 * 设计说明:
 * 1. datasetId 指定模板用例保存到哪个数据集。
 * 2. userId 指定用哪个测试用户运行这些用例，默认建议使用 1。
 * 3. overwrite 为 true 时允许重新生成已有类型模板；第一版默认 false，避免覆盖管理员手动调整过的用例。
 */
@Data
public class AgentEvalCoreTemplateCreateDTO {
    private Long datasetId;
    private Long userId;
    private Boolean overwrite;
}
