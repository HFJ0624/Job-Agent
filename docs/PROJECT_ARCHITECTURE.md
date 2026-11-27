# Job-Agent 项目架构与功能说明

> 分支：`wordbuddy-stage`　更新时间：2026-11-27
> 说明：本文以**当前源码**为依据（模块 POM、Java 包、Mapper XML、前端工程），不引用历史文档中的旧路径。

---

## 1. 项目定位

Job-Agent 是一个「AI 求职助手」：用户用自然语言提出求职需求，系统在同一轮对话里完成
**简历解析 → 岗位搜索 → 匹配打分 → 话术生成 → 投递/面试准备**，并把过程以任务卡片 + SSE 流式输出呈现。

它同时是一个**可讲解的 Agent 工程样本**：多 Agent 编排、ReAct 内核、预算、记忆、缓存、护栏、
模型网关、评测与可观测都按生产级边界拆分，而不是"一个 Prompt 打天下"。

---

## 2. 技术栈

| 层 | 选型 |
|---|---|
| 后端框架 | Spring Boot 3.x、Spring MVC、Sa-Token（登录态） |
| 持久化 | MySQL 8（MyBatis-Plus）、Redis（会话/限流/缓存辅助） |
| 向量 | pgvector（RAG 切片检索） |
| 对象存储 | MinIO（简历文件、导出文件） |
| 模型 | OpenAI 兼容协议（当前指向火山方舟 ARK：`deepseek-v4-1-flash` / `deepseek-v4-pro`） |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus（`apps/user`、`apps/admin`） |
| 构建 | Maven 多模块（4 个）、npm（前端） |

---

## 3. 模块架构

### 3.1 四个 Maven 模块

| 模块 | 类数 | 职责 |
|---|---:|---|
| **Job-Agent-Bootstrap** | 261 | 启动类、HTTP/SSE 接口、求职业务（简历/岗位/投递/沟通/面试/RAG）、业务 Mapper、端口适配器、定时任务 |
| **Job-Agent-Infra-Ai** | 180 | 完整 Agent 能力：聊天与任务用例、路由、多 Agent 编排、ReAct、工具、预算、记忆、缓存、护栏、评测、模型网关、AI 自有持久化 |
| **Job-Agent-Mcp-Tools** | 14 | MCP 协议与外部连接器（邮件/日历/通知/招聘平台/简历导出） |
| **Job-Agent-Framework** | 303 | 跨模块 DTO/VO、实体、枚举、异常、通用工具 |

### 3.2 依赖方向（单向，已核验）

```
Bootstrap ──→ Infra-Ai ──→ Framework
    │            │
    ├──→ Mcp-Tools ──→ Framework
    └──→ Framework
```

约束（构建期可验证）：

- `Infra-Ai` / `Mcp-Tools` 对 `com.job.bootstrap` 的引用数 = **0**；
- `Mcp-Tools` 不再依赖 `Infra-Ai`；
- 业务调用通过 **端口（Port）** 反转：接口定义在 Infra-Ai，实现在 Bootstrap 的 `integration/agent/**`。

### 3.3 包根

| 模块 | 根包 |
|---|---|
| Bootstrap | `com.job.bootstrap` |
| Infra-Ai | `com.job.agent`（Agent 能力）+ `com.job.config`（LangChain4j 装配） |
| Mcp-Tools | `com.job.mcp` |
| Framework | `com.job.common` |

---

## 4. 一次请求的完整链路

```
前端 POST /front/agent/chat/stream（Sa-Token）
  │
  ▼
Bootstrap: api/agent/front/AgentController
  │  只做 HTTP/SSE 适配：参数、登录身份、事件输出
  ▼
Infra-Ai: api/chat/AgentChatService（接口）
  └── application/chat/impl/AgentChatServiceImpl
        ├─ 护栏（注入检测 / 敏感操作 / PII）
        ├─ 意图识别：规则优先 → LLM 兜底（support/intent）
        ├─ ChatRequestRouter
        │
        ├─ 已有任务：严格按 agent_plan.runtime_mode 路由
        │     MULTI_AGENT / RESUME / REACT_SEARCH / TEMPLATE
        │
        ├─ 新任务：意图 + 关键词 + 开关依次询问三个入口
        │     JobSeekReactEntry / ResumeReactEntry / JobSearchReactEntry
        │
        ├─ 语义缓存（support/cache）：命中直接返回
        ├─ 上下文构建（support/context）：窗口 + 滚动摘要 + 预算
        ├─ 记忆注入（support/memory）：长期偏好 / 事实
        │
        ▼
    场景层
      ├─ 复合求职：scenario/jobseek
      │     PREPARE（条件合并 + 简历定位）
      │     → 画像 ∥ 搜索（条件独立才并行）
      │     → 节点成功即存产物（版本由 SeekVersionPolicy 决定）
      │     → 搜索为空直接返回（不编造匹配）
      │     → 匹配 → 校验报告 → SeekAnswerAssembler
      │
      ├─ 独立简历：scenario/resume
      ├─ 独立搜索：scenario/jobsearch
      └─ 历史模板：legacy/template（仍在服务历史任务）
        │
        ▼
    单 Agent 执行内核
      core/react/ReActRunner（反馈循环）
        ├─ 预算闸门 / 取消 / 轮次 / 截止时间
        ├─ 模型调用：model/gateway
        ├─ 工具调用：tool/registry（注册表）→ tool/impl（8 个业务工具）
        └─ 终结必须走 TerminalContract（结构化结果校验）
```

**SSE 事件**：`token`（逐字）、`progress`（节点开始/完成/等待输入/失败/完成）、`done`（最终答案与任务标识）。

---

## 5. Infra-Ai 包结构与功能

| 包 | 类数 | 功能 |
|---|---:|---|
| `api/chat` | 1 | `AgentChatService`：Bootstrap 唯一依赖的聊天接口 |
| `application/chat`（+`impl`） | 20 | 聊天用例、5 个辅助服务（计划生成/执行、记忆捕获/上下文/提取）、会话存储、Trace 组装、流式答复 |
| `application/task` | 3 | 父任务生命周期：条件领取、条件写回、取消 |
| `application/plan` | 2 | 参数解析（规则 + LLM 补步） |
| `application/routing` | 1 | `ChatRequestRouter`：四条链路选择 |
| `core/react` | 7 | `ReActRunner`、`AgentDefinition/Result/Context`、`TerminalContract` |
| `core/budget` | 3 | 父任务共享预算、并发闸门、线程上下文 |
| `core/event` | 3 | 进度事件与发布器 |
| `scenario/jobseek` | 8 | 复合求职：入口/Workflow/版本策略/产物/上下文/答复组装/线程池 |
| `scenario/jobsearch` | 7 | 独立岗位搜索（ReAct） |
| `scenario/resume` | 6 | 独立简历分析 + 简历选择 |
| `scenario/match` | 3 | 匹配节点、报告契约、预检 |
| `tool/impl` | 8 (+2) | 岗位搜索、简历评分、匹配、推荐、话术、面试准备、模拟面试复盘、知识库检索 |
| `tool/registry` | 7 | 工具注册表、Schema、执行结果、权限守卫 |
| `port` | 13 | 业务端口：搜索/简历/评分/匹配/推荐/RAG/话术/面试/复盘/连接器/实体解析/嵌入/沟通补偿 |
| `support/guardrail` | 7 (+1) | 注入、PII、敏感操作、工具权限、输出 JSON/实体校验 |
| `support/memory` | 10 (+1) | 规则/LLM 提取、冲突裁决、写策略、TTL、记忆服务 |
| `support/cache` | 4 | 语义缓存（用户隔离 + 相似度 + TTL） |
| `support/context` | 3 | 历史窗口、滚动摘要、Token 预算 |
| `support/intent` / `rewrite` / `eval` / `trace` | 6 | 意图识别、查询改写、评测匹配、Trace 接口 |
| `support/compensation` | 8 | 工具失败补偿（话术/邮件/不可逆外部动作） |
| `legacy/template` | 5 | 旧模板计划链路 |
| `model/gateway`（+`impl`） | 3 | 模型路由、重试、熔断、限流、扣费、调用日志 |
| `model/prompt`（+`impl`） | 2 | 提示词模板渲染与版本 |
| `model/turn` | 5 | ChatTurn 协议（消息/工具调用/工具规格） |
| `model/observability` | 2 | 观测接口与记录 |
| `role` | 5 | **角色包**：HR 沟通、面试邀请抽取、求职对话、结果总结、简历评分（LangChain4j Assistant） |
| `persistence/mapper` | 15 | AI 自有 Mapper（会话/消息/计划/运行/产物/模型配置/日志/记忆…） |

---

## 6. Bootstrap 包结构与功能

| 包 | 类数 | 功能 |
|---|---:|---|
| `api/front/<域>` | 23 | 用户端接口：auth、agent、communication、file、home、interview、job、jobapply、rag、resume、user |
| `api/admin/<域>` | 22 | 管理端接口：agent、ai、auth、company、dashboard、integration、interview、job、rag、user、workflow |
| `api/agent` | 1 | `AgentController`：聊天/SSE、任务状态、取消 |
| `api/mcp` | 1 | MCP 协议入口 |
| `application/<域>/service(.impl)` | 134 | 求职业务：admin/agent/communication/home/interview/job/jobapply/rag/resume/user/workflow |
| `infrastructure/persistence/<域>/mapper` | 46 | 业务 Mapper（其中 2 个有 XML：沟通记录、提醒列表） |
| `integration/agent/**` | 14 | 端口适配器（覆盖 13 个端口，resume 域两个） |
| `config` | 13 | Sa-Token、MyBatis、线程池、日志、模型 Bean 等 |
| `mcp` | 4 | MCP 客户端 |
| `scheduler` | 2 | 每日报告、跟进提醒 |

---

## 7. 核心机制

### 7.1 路由（`ChatRequestRouter`）

- **有 `planId`**：只读 `agent_plan.runtime_mode`，禁止关键词抢占（否则补参消息会被复合入口劫持，原任务无法续跑）；
- **无 `planId`**：意图 → 关键词 → 开关，依次询问三个 Entry，都不命中则走历史模板链路。

### 7.2 ReAct 内核（`ReActRunner`）

- 每轮检查：取消、截止时间、轮次、父预算、模型并发；
- **额度只在网关扣**（含重试/备用模型），Runner 只做闸门，避免一次请求扣两次；
- 消息严格配对：assistant 的每个 `toolCall` 必须有对应 tool 结果；
- 输出截断 / 无工具调用 / toolCallId 非法：各给有限次纠错，超限返回 `PROTOCOL_ERROR`；
- 终结必须单独一轮，并经 `TerminalContract` 做结构与业务校验。

### 7.3 预算与取消

| 项 | 默认值 |
|---|---|
| 总时限 | 300s |
| 模型调用 | 24 次 |
| 工具调用 | 30 次 |
| 模型并发 | 2 |

- 补参续跑用 `restore(...)` 延续已用额度，**等待用户时间不计入**；
- 取消：内存信号 + 立即持久化 `CANCELLED`（等待补参时也能取消）；
- 迟到结果不能覆盖终态（条件更新 + epoch 校验）。

### 7.4 任务状态机（`AgentTaskService`）

```
PLANNED / NEED_CLARIFICATION / FAILED
        │ claimForExecution（条件更新，只有一个请求能领取）
        ▼
      RUNNING ──→ COMPLETED / FAILED / WAITING_INPUT / CANCELLED
        ▲                │
        └── completeExecution 仅在 RUNNING 时写回（否则视为被取消/接管）
```

### 7.5 多 Agent 编排（`JobSeekWorkflow`）

- 固定骨架：准备 → 画像 ∥ 搜索 → 匹配 → 组装；
- **合法并行**：仅当关键词与城市都显式给出时才并行，否则串行（避免条件不完整导致空召回）；
- 节点成功即存产物，版本规则：
  - 画像 = 简历（更新时间 + 文本哈希）+ 目标方向
  - 候选 = 条件（城市/薪资/学历/关键词…）+ 简历 ID
  - 报告 = 条件版本 + 画像产物 ID + 候选产物 ID
- 同版本复用，不重复写；搜索为空直接返回真实空结果，不调匹配；
- 最终答复只由**可信产物**重建，不追加模型自由文本。

### 7.6 上下文（`AgentContextBuilder`）

1. 取窗口：`historyRounds * 2 + 1` 条（多取 1 条排除当前轮）；
2. 窗口外旧历史 → 增量滚动摘要；
3. 窗口内拼历史文本，按预算截断（记录 `truncated`）；
4. 返回 `BuiltContext(historyText, rollingSummary, truncated, tokens)`。

### 7.7 记忆（`support/memory`）

```
规则提取 →（LLM 兜底）→ WritePolicy（字段/置信度/敏感信息/疑问句）
        → ConflictGate：PASS / SKIP_EQUIVALENT / DEFER_TEMPORARY / CONFLICT_REVIEW
        → 落库（TTL 过期归档）→ 注入上下文
```

### 7.8 护栏与模型网关

| 能力 | 说明 |
|---|---|
| 输入护栏 | 注入特征、超长、敏感操作确认 |
| 输出护栏 | 工具输出 JSON 校验、实体必须来自工具结果、PII 脱敏 |
| 网关 | 路由（场景 × 档位）→ 配置 → **唯一扣费点** → 重试/备用模型 → 熔断 → 双维限流 → Trace |

排障口诀：`没有启用的模型路由` = 路由表缺 ACTIVE 行；`400 + Arrearage` = 账户欠费；
`401` = key 错；`400 model not found` = `model_identifier` 错。

---

## 8. 数据模型（61 张表，其中 AI 自有 32 张）

| 分组 | 表 |
|---|---|
| 模型与提示词 | `ai_model_config`、`ai_model_route`、`ai_model_call_log`、`ai_model_circuit_state`、`ai_prompt_template`、`ai_prompt_version` |
| 会话与对话 | `ai_conversation`、`ai_message` |
| 任务与执行 | `agent_plan`、`agent_plan_step`、`agent_run`、`agent_artifact`、`agent_tool` |
| 记忆 | `agent_long_term_memory`、`agent_memory_history`、`agent_user_memory_profile` |
| 评测 | `agent_eval_dataset`、`agent_eval_case`、`agent_eval_run`、`agent_eval_result` |
| 观测与治理 | `agent_trace_log`、`agent_trace_retention_policy`、`agent_observation_event`、`agent_observation_alert_rule`、`agent_observation_alert_record` |
| 主动服务 | `agent_action_item`、`agent_inbox_action_record`、`agent_daily_report_record`、`agent_daily_report_subscription`、`agent_follow_up_rule` |
| 工作流 | `agent_workflow_task`、`agent_workflow_task_log` |

求职业务表（简历/岗位/公司/投递/沟通/提醒/面试/用户等）共 29 张，归 Bootstrap 所有。

---

## 9. 前端结构

```
Job-Agent-Frontend/
├── apps/user      用户端（Vite + Vue3 + Element Plus）
│   └── src/{api, components, composables, pages}
└── apps/admin     管理端
```

关键文件：

| 文件 | 作用 |
|---|---|
| `apps/user/src/pages/AgentPage.vue` | Agent 对话页：消息、任务卡片、SSE、取消/重试/新任务 |
| `apps/user/src/api/agent.ts` | 聊天/SSE/任务状态接口封装 |
| `apps/user/src/composables/agent/` | 任务状态与标签（`useAgentTask`、`taskLabels`、`taskTypes`、`taskStorage`） |

SSE 行为：`token` 逐字追加、`progress` 更新任务卡片、`done` 校正最终答案并同步任务快照。

---

## 10. 功能清单

### 10.1 用户端

- 简历：上传、解析、评分、导出
- 岗位：搜索、筛选、收藏、推荐、偏好管理
- 匹配：简历 × 岗位匹配、硬条件校验、证据化说明
- 沟通：打招呼话术生成、沟通记录、提醒
- 投递：投递记录、投递决策、跟进
- 面试：面试准备、模拟面试、复盘、错题、学习计划
- Agent：对话、复合任务、任务卡片、SSE、取消/重试/补参
- 知识库：RAG 检索（文档/切片/引用）
- 账户：登录注册、地址、个人资料

### 10.2 管理端

- Agent 运营：计划、行动项、Trace、观测告警、记忆、工具 Schema、评测
- 模型管理：模型配置、路由、提示词版本
- 业务管理：公司、岗位、用户、面试题库、模拟面试、RAG 文档
- 工作流：任务列表与日志
- 集成：外部连接器、MCP

---

## 11. 扩展指南

| 要做什么 | 改哪里 |
|---|---|
| 新增业务流程（场景） | `com.job.agent.scenario.<name>` + 在 `ChatRequestRouter` 增加路由 + Entry 的 `shouldHandle` |
| 新增 Agent 工具 | `tool/impl/XxxTool` + `AgentToolInvoker` 注册 + `agent_tool` 配置；需要业务数据则先在 `port` 定义接口、`integration/agent` 实现 |
| 新增业务端口 | `com.job.agent.port.XxxPort`（Infra-Ai） + `com.job.bootstrap.integration.agent.xxx.XxxAdapter` |
| 新增模型供应商 | `ai_model_config` 插一行 + `ai_model_route` 指向它；不支持流式会自动降级为阻塞 |
| 改提示词 | `ai_prompt_template` / `ai_prompt_version`（无需发版），或 `com.job.agent.role` 的 Assistant 注解 |
| 新增 HTTP 接口 | `api/front|admin/<域>/XxxController` + `application/<域>/service` |
| 新增 Mapper | 业务放 `infrastructure/persistence/<域>/mapper`；AI 放 `com.job.agent.persistence.mapper` |

---

## 12. 运行与验证

```bash
# 后端（需 DB_PASSWORD、MinIO/Redis 配置；profile: dev,local）
cd Job-Agent-backend
mvn -pl Job-Agent-Bootstrap -am clean verify

# 前端
cd Job-Agent-Frontend/apps/user
npm run build
```

当前基线（2026-11-27 实测）：

- 单测 **378 全绿**；
- `clean package` 成功，启动 **~13s**；
- 2 个 XML 分页查询已用真实 MyBatis 执行验证（沟通 2 条、提醒 1 条）；
- 模型配置指向 ARK，key 与模型 ID 已直连验证 `200`。

---

## 13. 已知边界

- 历史 `legacy/template` 链路仍在服务旧任务，未删除；
- 部分 AI 自有表与业务表存在字段冗余（迁移期兼容），未做范式化；
- SSE 的断线重连依赖前端刷新 + 任务快照，不做事件回放；
- 本地文件与远程 `origin/wordbuddy-stage` 尚有未推送提交。
