package com.job.common.vo.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能:Agent Eval 单条失败结果诊断展示对象。
 *
 * 设计说明:
 * 1. 第一版只做规则诊断，不调用大模型，保证诊断速度稳定、成本为零。
 * 2. summary 给管理员一个一句话结论。
 * 3. rootCauses 说明可能根因，suggestions 给出下一步排查动作，evidence 展示判断依据。
 */
@Data
public class AgentEvalResultDiagnosisVO {
    private Long resultId;
    private Integer passStatus;
    private String failureType;
    private String priority;
    private String summary;
    private List<String> rootCauses = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private List<String> evidence = new ArrayList<>();
}
