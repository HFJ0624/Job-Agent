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
| 前端进度展示（页面 UI） | ✅ `AgentPage` 任务卡片（节点/摘要/等待问题/失败原因/操作按钮） | ✅ `vue-tsc` + `vite build` 通过 | ❌ 未跑真实 SSE |
| 页面续跑/重试/取消/新任务 | ✅ 四个动作 + 按会话保存 planId + 有界轮询恢复 | ✅ 类型检查/构建 | ❌ |
| 网关重试共享父预算 | ✅ `TaskBudgetContext`：真实 HTTP 尝试计入额度，剩余时间不足即停 | ✅ 编译 + 既有回归 | ❌ |

**未完成**：① 预算/取消/独立简历未写专项单测；② 真实模型、MySQL、SSE 端到端未执行；③ 并发池拒绝降级的专项验证未做；④ 多份简历选择后续跑的端到端演示未跑。

## 8. 改动清单

- 新增：`agent/task/TaskExecutionBudget`、`TaskBudgetRegistry`、`AgentTaskStatusService`；`agent/progress/AgentProgressEvent`、`TaskProgressPublisher`、`SseTaskProgressPublisher`；`agent/resume/ResumeReactEntry`；`sql/09_migration_phase3_task_interaction.sql`
- 修改：`ReActRunner`（预算占用/取消/原因码）、`AgentRunExecutionHooks`（budget）、`JobSeekWorkflow`（进度事件+预算透传）、`ResumeAgentNode`/`MatchAgentNode`（budget 参数重载）、`JobSeekReactEntry`（预算/进度/待办问题/取消）、`AgentChatServiceImpl`（简历分发 + publisher）、`AgentController`（status/cancel + SSE publisher）、`AgentPlan`/`AgentPlanStatus`（pendingQuestion/cancelledAt/额度/CANCELLED）、`apps/user/src/api/agent.ts`（进度/状态/取消 API）


## 9. 页面交互（最后一轮收口，2026-11-03）

**AgentPage 任务卡片**（轻量，复用现有样式，不重设计页面）：
- 展示任务状态（执行中/等待补充/已完成/失败/已取消）、三个节点状态、最近动作摘要、等待问题、失败/取消原因；
- 并行节点各自独立状态，不按全局 seq 串行推进；节点完成后不回退（幂等）；
- 只显示实际执行的节点（独立简历分析只显示简历节点）。

**四个动作**：
| 动作 | 行为 | 关键约束 |
|---|---|---|
| 继续/补充 | WAITING_INPUT 时发送自动带 `planId` | 不重新分类/重新创建任务 |
| 重试 | `retry=true` + 原 `planId` | 复用已完成上游产物，只重跑失败节点 |
| 取消 | `POST /task/{planId}/cancel` | 取消后迟到 token/done 不能把状态改回成功；已完成结果保留 |
| 新任务 | 清理本地续跑标识 | 不删除历史、不自动取消上一任务 |

**刷新恢复**：按会话保存 `planId`（localStorage `jobAgentTask:{conversationId}`），进入会话时 `GET /task/{planId}` 恢复；RUNNING 有界轮询（3s × 最多 40 次），终态/切会话/卸载即停；不自动重发聊天请求；查询失败不把任务显示为失败。

**字段映射**：`PLAN_STATUS_MAP`（NEED_CLARIFICATION→WAITING_INPUT、COMPLETED→SUCCEEDED 等）集中在页面一处；事件与快照状态统一归一化。

**构建验证**：后端 `mvn -pl Job-Agent-Bootstrap -am test` → **359 全绿**；前端 `npx vue-tsc --noEmit` 无错误、`npm run build` 成功（chunk 体积警告为既有问题）。

**未执行**：真实模型 + MySQL + SSE 端到端；多份简历选择后续跑的真实演示；真实并行事件顺序下的视觉验收。


## 10. 最终审查修复记录（F1–F6，2026-11-03）

| 编号 | 问题 | 修复 |
|---|---|---|
| F1 | 预算重复扣：Runner 扣一次、网关每次真实尝试又扣一次；搜索节点未继承父预算；并发闸门未使用；超时不收紧 | Runner 只做闸门与**并发闸门**（tryEnterModelCall/exit 配对），额度**唯一扣减点移到网关真实 HTTP 尝试**；搜索节点显式设置执行钩子（含 budget）；`future.get` 取父剩余时间；模型 HTTP 超时取 `min(配置, 剩余)`；执行耗时落库（`execution_millis_used`），补参延续 |
| F2 | 父任务无状态分发/领取保护；断流后前端盲目重提 | 入口按 COMPLETED/RUNNING/FAILED/NEED_CLARIFICATION/CANCELLED 分发；`claimForExecution` 状态条件领取；`completeExecution` 条件写回（非 RUNNING 不覆盖）；断流时前端优先查询原任务 |
| F3 | 补参丢条件/丢已选简历；节点 NEED_INPUT 被当成功 | 任务上下文 `{constraints, resumeId}` 每轮写回父任务并按两种历史格式读回；补参沿用已选简历；简历节点 NEED_INPUT 传播为父任务 WAITING_INPUT（复合 + 独立入口一致） |
| F4 | 候选产物按 `{jobId, raw}` 保存却按平铺读取；产物要等两个节点都成功才保存 | `JobCandidateArtifactCodec` 兼容 wrapper/平铺两种结构（含定点单测）；画像/候选节点成功即保存；画像版本含显式目标方向、报告版本含上下游产物 ID |
| F5 | 取消只改内存不落库；等待补参时无法取消；迟到事件覆盖取消 | 取消接口立即持久化 CANCELLED（无活动执行链也可取消）；入口/独立简历入口取消优先，条件写回不会覆盖 CANCELLED；页面忽略取消后的迟到事件，快照不推翻本地取消 |
| F6 | 并行只发 node_started；独立简历进度丢失；页面固定三节点；旧查询污染当前会话 | 并行分支补齐两个 node_completed；独立简历入口透传 publisher；页面节点按真实事件/快照动态生成；查询/轮询带批次号，旧响应丢弃 |

**定点验证**：`JobCandidateArtifactCodecTest`（F4）、`JobSeekWorkflowTest`（复用/版本）；后端 **361** 单测全绿；前端 `vue-tsc` + `vite build` 通过。

**仍未执行**：真实模型 + MySQL + SSE 端到端（三组人工场景：正常复合 / 补参与失败恢复 / 取消与刷新恢复）；`sql/10` 需在真库执行。**状态如实**：以上为代码修复 + 编译/单测/构建验证，不是真实环境联调结论。


## 11. 启动修复（2026-11-03，Flyway 误连 PostgreSQL）

**现象**：启动报 `Unsupported Database: PostgreSQL 18.3`（Flyway 初始化失败）。
**根因**：`RagConfig` 声明了 pgvector 的 `DataSource`（`ragDataSource`），Spring Boot 的 `DataSourceAutoConfiguration` 见到已存在 `DataSource` 就退让——上下文里只剩 PG 数据源，MyBatis 与 Flyway 都指向 PostgreSQL；Flyway 的 `flyway-mysql` 模块也不含 PG 支持，于是启动失败。

**修复**：
1. 新增 `PrimaryDataSourceConfig`：按 `spring.datasource.*` 显式创建 MySQL 主数据源并标 `@Primary`，MyBatis-Plus / Flyway / 事务回到业务库；RAG 仍用 `@Qualifier("ragDataSource")` 的 PG 池；
2. `JobAgentApplication` 排除 `FlywayAutoConfiguration`：任务4 只是脚手架、`db/migration` 为空、业务库一直由 `sql/` 手工脚本维护，默认不该自动迁移（将来启用需移除 exclude + 放 `V1__baseline.sql` + `FLYWAY_ENABLED=true`）；
3. `application.yml` 保留 flyway 配置（默认 `enabled:false`）作为启用说明；
4. `RagConfig` 的 PG 池设置 `initializationFailTimeout=-1`：PG 暂不可达不阻断应用启动，首次检索时再建连（按已有降级逻辑处理）。

**验证**：
- 新增 `PrimaryDataSourceConfigTest`（主数据源必须是 MySQL 且 @Primary）；
- 打包启动实测：`flywayInitializer` 不再出现（0 次），原始 PG 报错消失；
- 后端 **363** 单测全绿；
- 沙箱环境无 MySQL 口令 / MinIO 密钥，后续启动会停在环境配置（非代码问题）：本地需设置 `DB_PASSWORD` 与 MinIO 的 access/secret。
