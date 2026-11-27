# Agent 代码地图

> 目的：让"排查一个问题"不用翻 20 个包。按下面的顺序读，5 分钟能定位到入口。

## 分层（依赖方向：orchestration → domain → runtime；support 不依赖 domain，runtime 不依赖业务）

```
agent/
├── runtime/             执行内核：与业务无关，可整体复用到其他项目
│   ├── react/           ReActRunner、AgentDefinition、AgentResult、AgentRunContext、
│   │                    TerminalContract、TerminalToolContract、AgentRunExecutionHooks
│   ├── budget/          TaskExecutionBudget、TaskBudgetRegistry、TaskBudgetContext
│   ├── progress/        AgentProgressEvent、TaskProgressPublisher、SseTaskProgressPublisher
│   ├── run/             AgentRunService（状态机/原子领取/检查点）
│   ├── artifact/        AgentArtifactService（结构化产物，按版本复用）
│   ├── executor/        AgentToolInvoker（统一工具执行边界：身份桥接/Guard/Trace）
│   └── schema/          AgentToolGuard、AgentToolSchemaRegistry（工具参数契约）
├── domain/               业务 Agent（输入/输出/契约）
│   ├── resume/          ResumeAgentNode、ResumeProfile(+Contract)、ResumeSections、
│   │                    ResumeSelectionService（简历定位统一规则）、ResumeReactEntry
│   ├── job/             JobCandidates(+Adapter)、SearchConstraints、JobSearchWorkflow、
│   │                    JobSearchReactEntry、ReactChatPreparationService、ReactSearchProperties
│   ├── match/           MatchAgentNode、MatchReport、MatchReportContract
│   └── tools/           8 个业务 Tool（搜索/推荐/简历/匹配/面试/话术/RAG）+ resolver
├── orchestration/        编排与任务
│   ├── seek/            JobSeekWorkflow、JobSeekReactEntry、JobCandidateArtifactCodec、
│   │                    JobSeekExecutorConfig（有界并行池）
│   ├── plan/            AgentPlan*（模板、参数抽取、补步、重规划）
│   └── task/            AgentTaskService（父计划/领取/写回/额度）、AgentTaskStatusService（快照）
├── support/              横切能力
│   ├── guardrail/       输入护栏、PII 脱敏、注入/越权检测
│   ├── memory/          长期记忆抽取、冲突门控、过期清理
│   ├── cache/           答案语义缓存
│   ├── intent/          意图规则 + LLM 兜底
│   ├── context/         多轮上下文与 Token 预算
│   ├── compensation/    计划补偿（邮件/话术等）
│   ├── rewrite/         查询改写
│   └── eval/            Eval 匹配
└── config/              JobAgentConfig（Agent 相关 Bean 装配）
```

## 三个入口（`*Entry` 是唯一拿到 userId/conversationId 的地方）

| 入口 | 触发 | 骨架 |
|---|---|---|
| `orchestration/seek/JobSeekReactEntry` | 简历 + 岗位/匹配的复合请求 | `JobSeekWorkflow`：PREPARE → 画像∥搜索 → JOIN → 匹配 → 校验 → 答复 |
| `domain/job/JobSearchReactEntry` | 只找岗位 | `JobSearchWorkflow`：校验 → ReAct 搜索 → 验真 → 答复 |
| `domain/resume/ResumeReactEntry` | 只分析简历 | 简历定位 → `ResumeAgentNode` → 画像产物 → 答复 |

三者共用：`TaskBudgetRegistry`（父预算/取消）、`AgentRunService`（子 run 状态）、`AgentProgressEvent`（进度）、`AgentArtifactService`（产物）。

## 排查常用跳转

- "为什么说没找到岗位" → `JobSearchWorkflow.validateAndRespond`（EMPTY/FOUND 验真）
- "为什么候选字段为空" → `JobCandidateArtifactCodec`（`{jobId, raw}` 结构）
- "为什么任务重复执行/取消后变成功" → `AgentTaskService.claimForExecution / completeExecution`
- "为什么补参丢条件/丢简历" → `JobSeekWorkflow.mergeConstraints` + `ResumeSelectionService`
- "为什么一直显示执行中" → `AgentProgressEvent` 的 node_started/node_completed 是否成对
- "为什么模型调用次数不对" → `TaskExecutionBudget` 唯一扣费点在网关（`AiModelGatewayServiceImpl`），Runner 只做闸门

## 命名约定

`*Entry`（对话入口）→ `*Workflow`（固定骨架）→ `*Node`（单 Agent 执行）→ `*Contract`（终结契约）→ `*Service`（状态/产物/工具）→ `*Properties`（配置）。
新增 Agent 时按此命名，并只允许 `orchestration → domain → runtime` 方向依赖。
