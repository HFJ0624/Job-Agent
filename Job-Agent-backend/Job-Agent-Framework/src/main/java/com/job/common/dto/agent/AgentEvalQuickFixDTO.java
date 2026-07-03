package com.job.common.dto.agent;

import lombok.Data;

/**
 * 功能:Agent Eval 诊断快捷修复参数。
 *
 * 设计说明:
 * 1. 快捷修复只允许修改 Eval 用例，不修改真实业务数据。
 * 2. actionType 使用白名单字符串，后端统一校验，避免前端传入任意字段更新。
 * 3. 第一版只支持低风险操作，例如清空过严断言、用实际工具回填期望工具。
 */
@Data
public class AgentEvalQuickFixDTO {
    private String actionType;
}
