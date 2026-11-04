# 项目地图（维护者入口）

> 用途：想改一个功能时，先在这里找"归谁管"，再进代码。类名/路径以当前工作区为准。

## 1. Maven 模块与职责

| 模块 | 职责 | 不该放什么 |
|---|---|---|
| Job-Agent-Bootstrap | 启动、Controller、业务 Service、Agent 实现、Mapper、配置 | —— |
| Job-Agent-Framework | 共用实体、DTO/VO、异常、枚举 | 不依赖 Bootstrap |
| Job-Agent-Infra-Ai | 模型/嵌入等 AI 基础设施接口与配置 | 不依赖 Bootstrap |
| Job-Agent-Mcp-Tools | MCP 协议、外部连接器 | 不依赖 Bootstrap |

## 2. Agent 调用链（Controller → 场景 → 内核）

```
AgentController (/front/agent/chat、/chat/stream、/task/{planId})
  └─ AgentChatService.streamChat/chat            ← 统一分发（同步/流式共用）
      ├─ 复合：JobSeekReactEntry → JobSeekWorkflow
      │    └─ ResumeAgentNode ∥ JobSearchWorkflow → MatchAgentNode
      ├─ 独立搜索：JobSearchReactEntry → JobSearchWorkflow
      ├─ 独立简历：ResumeReactEntry → ResumeAgentNode
      └─ 旧模板：AgentPlanExecutorService（仍在用，勿删）
  └─ 每个节点：ReActRunner（反馈循环）→ AgentToolInvoker（工具边界）→ 业务 Tool
```

关键类（读这十来个就够）：

| 层 | 类 | 作用 |
|---|---|---|
| 入口 | `controller/front/AgentController` | HTTP/SSE 协议、任务状态/取消接口 |
| 分发 | `service/impl/AgentChatServiceImpl` | 会话、护栏、路由、旧链路、结果落库（**待拆**，见重构报告） |
| 复合 | `orchestration/seek/JobSeekWorkflow` | 固定骨架：准备→画像∥搜索→匹配→验真→答复 |
| 复合 | `orchestration/seek/SeekVersionPolicy` | 三类产物版本规则（复用正确性） |
| 任务 | `orchestration/task/AgentTaskService` | 父任务领取/写回/额度/快照 |
| 搜索 | `domain/job/JobSearchWorkflow` | 搜索验真、EMPTY/FOUND |
| 简历 | `domain/resume/ResumeAgentNode` + `ResumeSelectionService` | 画像与证据回溯、简历定位 |
| 匹配 | `domain/match/MatchAgentNode` | 只读预览、报告校验 |
| 内核 | `runtime/react/ReActRunner` + `TerminalContract` | 反馈循环、终结契约 |
| 内核 | `runtime/budget/TaskExecutionBudget` | 父预算（网关唯一扣费点） |
| 内核 | `runtime/executor/AgentToolInvoker` | 可信身份、Guard、Trace、工具分发 |
| 产物 | `runtime/artifact/AgentArtifactService` | 结构化产物按版本复用 |
| 进度 | `runtime/progress/*` | SSE 节点事件 |

## 3. 业务模块导航（非 Agent）

| 业务 | Controller | Service | 数据 |
|---|---|---|---|
| 简历 | `controller/front/JobResumeController` | `service/impl/JobResumeServiceImpl` | `job_resume` |
| 岗位 | `controller/front/JobPositionController` | `service/impl/JobPositionServiceImpl` | `job_position` |
| 匹配 | `controller/front/JobMatchController` | `service/impl/JobMatchServiceImpl` | `job_match_record` |
| 模拟面试 | `controller/front/Interview*` | `service/impl/Interview*` | `interview_*` |
| 知识库 | `controller/admin/Rag*` | `rag/*` | PostgreSQL(pgvector) |
| 管理端 | `controller/admin/*` | `service/impl/*` | 同业务库 |

## 4. 新链路 vs 旧链路

- 开关：`job.agent.react-search.enabled`（只影响**新任务**；已有任务按持久化 `runtimeMode` 路由）。
- 新链路：`WORKFLOW_MULTI_AGENT` / `WORKFLOW_RESUME` / `WORKFLOW_REACT`。
- 旧链路：`TEMPLATE`，由 `AgentPlanExecutorServiceImpl` 执行；仍被大量历史任务和确认交互使用，**不要删除**。
- 找不到某功能时：先看 `AgentChatServiceImpl.chat()` 的分发顺序（复合 → 简历 → 搜索 → 旧链路）。

## 5. 推荐阅读顺序

1. `AgentController.streamChat`（协议与任务接口）
2. `AgentChatServiceImpl.chat()`（路由与事务边界）
3. `JobSeekWorkflow.execute`（业务骨架）
4. `SeekVersionPolicy`（产物复用规则）
5. `ResumeAgentNode` / `JobSearchWorkflow` / `MatchAgentNode`（三个 Agent）
6. `ReActRunner` + `TerminalContract`（执行协议）
7. `AgentToolInvoker`（工具边界）
8. `TaskExecutionBudget` + `AgentRunService`（预算与子 run）
9. `AgentArtifactService`（产物）
10. `AgentPage.vue` 的 `useAgentTask`（前端任务状态）
