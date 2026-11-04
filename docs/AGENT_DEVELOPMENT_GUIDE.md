# Agent 扩展指南（怎么加、怎么改、怎么查）

## 1. 新增一个 Tool

1. 在 `agent/tool/impl/` 新建类，方法签名保持 `JobSearchTool.searchJobs` 这种 `Class.method` 形式；
2. 在 `runtime/executor/AgentToolInvoker` 注册（参数映射 + 白名单），并补 `AgentToolSchemaRegistry` 的 Schema；
3. 若需要确认/副作用：在 `support/guardrail` 标注副作用等级，前端确认协议不变；
4. 单测：`AgentToolInvokerTest` 增加一个参数映射用例。

> 新增后检查：工具名重复会覆盖，启动日志需能看出注册数量（后续可加启动校验）。

## 2. 新增一个 Agent（终结契约 + 节点）

1. 在 `domain/<场景>/` 新建 `XxxProfile`（record）+ `XxxContract implements TerminalContract<XxxProfile>`（`toolName()` + `parseTyped()` + `outcomeOf()`）；
2. 新建 `XxxAgentNode`：构造 `AgentDefinition`（系统提示、工具白名单、终结工具、轮次/工具/截止预算），调用 `ReActRunner.runWithTerminal`；
3. 节点内做**证据/范围校验**（如引用必须来自原文、jobId 必须来自候选），失败返回原因码；
4. 需要产物就 `AgentArtifactService.save(...)`，版本用 `SeekVersionPolicy` 统一规则。

## 3. 接入现有 Workflow

- `JobSeekWorkflow.execute` 内按骨架加节点：先判断前置产物，再执行，再校验；
- **成功节点自己保存产物**，不要等 JOIN 统一保存；
- 并行节点：`nodeExecutor.submit`，各自 `try/finally` 清理线程钩子，父任务预算显式传入（`AgentRunExecutionHooks.set`）；
- 进度事件成对：`node_started` / `node_completed|failed`。

## 4. 常见修改点

| 想改什么 | 改哪里 |
|---|---|
| 搜索条件（城市/薪资/学历/方向） | `scenario/jobsearch/SearchConstraints`（硬条件与 targetRole 复核） |
| 画像版本/复用规则 | `scenario/jobseek/SeekVersionPolicy` |
| 提示词 | 各 `*Node` 的 `systemPrompt(...)`（或 Prompt 模板表） |
| 模型场景/超时/重试 | 模型路由表 + `AiModelGatewayServiceImpl`（额度唯一扣费点） |
| 预算数值 | `runtime/budget/TaskExecutionBudget.DEFAULT_*` |
| SSE 事件名/载荷 | `runtime/progress/AgentProgressEvent` + `AgentController` + 前端 `api/agent.ts` |
| 任务状态语义 | `orchestration/task/AgentTaskService` + `AgentPlanStatus` |

## 5. 排查手册

| 现象 | 先看 |
|---|---|
| 重复执行/取消后变成功 | `claimForExecution` / `completeExecution` 条件是否生效；`runtimeMode` 路由 |
| 补参丢条件/丢简历 | `JobSeekWorkflow` 是否在解析简历**之前**合并条件；Entry 是否用 `mergeContext` 合并而非覆盖 |
| 产物不复用（每次都重跑） | 保存与查找的版本入参是否一致（`SeekVersionPolicy`） |
| 一个节点失败另一个产物也没了 | 节点成功路径内是否已 `saveArtifact`（不要在 JOIN 统一保存） |
| SSE 不更新 | 事件是否在节点真实完成时发布；前端是否用 `expectedPlanId` 过滤了迟到事件 |
| 模型调用次数不对 | 网关是否唯一扣费；Runner 不应再扣一次 |

## 6. 旧链路与临时适配

- `AgentPlanExecutorServiceImpl` 是旧模板链路，仍在服务历史任务；新增能力**不要**往这里塞。
- `JobSearchReactEntry` 等 Entry 是薄入口，只做身份/会话/预算/状态，不写业务规则。
- 临时适配（如 Entry 内的上下文合并）在拆分完成后可迁移到 `SeekContextResolver`。
