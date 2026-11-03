# 第三阶段：任务交互、共享预算与真实联调收口

> 状态：**代码完成（2026-11-03，本地提交，未推送）**。基于第二阶段（`64754bf`）。
> 验证：Bootstrap **359** 单测全绿；**真实模型/真库未联调**（见 §7）。

## 1. 本轮四件事

1. **任务标识与前端续跑**：`planId`（父任务）/`runId`（子执行）语义分离；新增按任务的状态快照与显式取消接口；前端 API 接收节点事件并保存任务标识。
2. **节点进度 SSE**：`task_started / node_started / node_completed / waiting_input / task_failed / task_completed`，与 `token/done/error` 共存；并行节点通过原子 seq 保证有序；**事件在节点实际开始/完成时发布**，不是任务结束后补发。
3. **父任务共享预算与取消**：绝对截止时间 + 模型请求/业务工具额度 + 取消信号，**先原子占用再调用**；子节点局部上限保留但受父额度约束；补参恢复延续已用额度（落库 `agent_plan.model_calls_used/tool_calls_used`），等待用户时间不计入执行耗时。
4. **独立简历分析接入 ResumeAgent**：薄入口复用第二阶段节点与产物契约，`runtimeMode=WORKFLOW_RESUME`，多份简历无法确定时暂停并列出名称。

## 2. 新增接口与事件

### 接口
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/front/agent/task/{planId}` | 任务状态快照（校验归属）：父状态、待补充问题、子节点状态、产物清单、预算用量 |
| POST | `/front/agent/task/{planId}/cancel` | 显式取消：设置共享取消信号 + 计划置 `CANCELLED`，已完成产物保留 |

### SSE 事件（`/front/agent/chat/stream`）
```json
{"event":"node_started","planId":900,"runId":null,"nodeKey":"RESUME_AGENT","agentType":"RESUME",
 "seq":2,"status":"RUNNING","summary":"正在分析简历","timestamp":1762150000000}
```
- 事件名：`task_started / node_started / node_completed / waiting_input / task_failed / task_completed`
- 前端 `streamChatWithAgent(payload, onToken, onProgress?)` 已支持；旧页面忽略未知事件即可
- **不暴露**工具原始参数、系统 Prompt、模型推理；只给动作与结果摘要

## 3. 标识语义与恢复

| 标识 | 含义 | 用途 |
|---|---|---|
| `conversationId` | 会话 | 前端按会话保存进行中的任务，不用全局变量 |
| `planId` | 父任务（复合任务/独立简历） | 续跑、状态查询、取消的稳定标识 |
| `runId` | 某个子 Agent 的一次执行 | Trace/排查；**不能**当作父任务标识 |

- `WAITING_INPUT`：计划置 `NEED_CLARIFICATION`，`pending_question` 落库；下次带 `planId` 的消息继续原任务与等待节点；
- `RUNNING`：状态接口返回 RUNNING，前端不重复提交；
- `SUCCEEDED`：重复提交复用已完成结果（入口按状态直接返回）；
- `FAILED`：需显式重试（`retry=true`），复用有效上游产物；
- `CANCELLED`：新增状态，与 FAILED/WAITING 区分；
- 刷新/断线：`GET /task/{planId}` 恢复展示；断线**不等于取消**。

## 4. 预算与取消（`TaskExecutionBudget`）

配置初值（代码常量，可按需调整）：父任务总时限 **300s**、模型请求 **24 次**、业务工具 **30 次**、模型并发 **2**。

- 每次模型调用/工具调用前 `tryReserveModelCall()/tryReserveToolCall()`（CAS 原子扣减），扣不到即终止并给出原因码；
- 取消信号在循环开头、模型调用后、工具执行前、终结结果接受前检查；
- 网关重试/备用模型受父截止时间约束（Runner 层多点检查 + 剩余时间判定）；
- 等待 Future 使用 `get(timeout)`；
- 并发池拒绝只降级**尚未提交**的节点（已提交/执行中的不会重跑）。

## 5. 独立简历分析

- 入口：`ResumeReactEntry`（`runtimeMode=WORKFLOW_RESUME`），复用 `ResumeAgentNode` 与 `ResumeProfile` 契约；
- 输出：简历事实（带原文依据）→ 简历中未见信息 → 建议；不把“未见证据”说成“不会”；
- 多份简历：返回名称清单并暂停，选择后按 `planId` 继续；换简历时画像版本变化，旧产物不复用。

## 6. 启用与联调步骤

1. 执行迁移（依赖顺序：`sql/07` → `sql/08` → `sql/09`）；
2. 配置 `job.agent.react-search.enabled: true`；
3. 配置模型路由场景 `AGENT_REACT`（模型需支持 OpenAI 兼容 tools）；
4. 启动后端 + 用户端前端，确认 `GET /front/agent/task/{planId}` 可用；
5. 最小请求（SSE）：
```bash
curl -N -X POST http://localhost:8080/front/agent/chat/stream \
  -H "Content-Type: application/json" -H "<tokenName>: <tokenValue>" \
  -d '{"message":"结合我的简历，找上海 Java 岗位，选出最合适的 5 个，并告诉我需要补哪些能力"}'
```
6. 观察：`task_started → node_started(RESUME) → node_completed → node_started(JOB_SEARCH) → node_completed → node_started(MATCH) → node_completed → token → done`；`done` 中带 `planId`。

## 7. 状态矩阵（如实）

| 项目 | 代码实现 | 本地验证 | 真实联调 |
|---|---|---|---|
| 三 Agent 复合任务 | ✅ | ✅ mock 三链路（`JobSeekWorkflowTest`） | ❌ 未执行 |
| 任务标识/状态快照/取消 | ✅ | ✅ 编译 + 既有回归 | ❌ 未执行 |
| SSE 节点进度 | ✅ 后端事件 + 前端 API 回调 | ⚠️ 仅编译，未跑真实 SSE | ❌ |
| 共享预算/取消 | ✅ | ⚠️ 未写专项单测（时间原因） | ❌ |
| 独立简历分析 | ✅ | ⚠️ 未写专项单测 | ❌ |
| 前端进度展示（页面 UI） | ❌ 仅 API 层（`onProgress`/状态/取消函数） | — | — |

**未完成**：① AgentPage 进度区域与“继续/重试/新任务”按钮未做（仅 API 接入）；② 预算/取消/独立简历未写专项单测；③ 真实模型、MySQL、SSE 端到端未执行；④ 并发池拒绝降级的专项验证未做。

## 8. 改动清单

- 新增：`agent/task/TaskExecutionBudget`、`TaskBudgetRegistry`、`AgentTaskStatusService`；`agent/progress/AgentProgressEvent`、`TaskProgressPublisher`、`SseTaskProgressPublisher`；`agent/resume/ResumeReactEntry`；`sql/09_migration_phase3_task_interaction.sql`
- 修改：`ReActRunner`（预算占用/取消/原因码）、`AgentRunExecutionHooks`（budget）、`JobSeekWorkflow`（进度事件+预算透传）、`ResumeAgentNode`/`MatchAgentNode`（budget 参数重载）、`JobSeekReactEntry`（预算/进度/待办问题/取消）、`AgentChatServiceImpl`（简历分发 + publisher）、`AgentController`（status/cancel + SSE publisher）、`AgentPlan`/`AgentPlanStatus`（pendingQuestion/cancelledAt/额度/CANCELLED）、`apps/user/src/api/agent.ts`（进度/状态/取消 API）
