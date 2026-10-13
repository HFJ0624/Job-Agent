# Job-Agent 下一阶段技术优化路线图

> 配套报告：`docs/agent-core-audit.md`（代码级审计结论）。
> 目标约束：**不是商业化产品，是通过本项目掌握 Java Agent 开发全链路，并作为求职实习的核心技术项目。**
> 原则：渐进式演进，不推翻重做；不引入微服务/K8s/多 Agent 集群；不堆业务按钮；所有改动考虑原有功能兼容。
> 本轮只出方案，不实施改造。

---

## 1. 下一阶段总体优化方向

按"学习价值 × 工程深度 × 面试价值 × 可控性"四个维度，下一阶段聚焦四条主线：

| 主线 | 目标 | 对应审计发现 |
|---|---|---|
| **A. 执行可靠性** | 让 Executor 从"能跑"变成"可信"：步骤级幂等、失败恢复、超时与重试 | Executor 2 个 P0（无步骤级幂等、失败锁死）；Workflow 2 个 P0（1 小时轮询、邮件重复发） |
| **B. 智能内核升级** | 规划从模板走向"规则 + LLM"混合；RAG 真正进入主链路；上下文工程补多轮与预算 | Planner 纯模板；RAG 预检索死代码；无多轮历史回灌 |
| **C. 质量基座** | 让 `mvn test` 全绿，补主链路单测与集成测试；Eval 异步化可重复 | 标准构建编译失败；0 集成测试；Eval 手工触发 |
| **D. 名实相符** | 死代码层做"激活或正名"二选一，消除面试叙事风险 | Infra-Ai 死代码、MCP-Tools 无协议、Redis/SSE 宣称无实现 |

**执行节奏建议**：先 C（基座，1 周内可完成，解锁"测试全绿"叙事）→ 再 A（可靠性，Java 深度主线）→ 再 B（智能升级，学习价值主线）→ 持续 D（随手清理）。

---

## 2. 候选优化任务全集（12 项，按综合优先级排序）

> 评分：学习价值 / Java 深度 / 面试价值 各 1-5 星；复杂度 1-5（1 最低）。
> "修改数据库"指是否需要加表/加字段；"新增依赖"指是否需要引入新库。

### 任务 1（P0）：Executor 步骤级幂等与失败恢复执行

- **当前实现**：计划级幂等（COMPLETED/FAILED 直接返回旧结果）；循环不检查步骤状态；失败计划永久锁死（`AgentPlanExecutorServiceImpl.java:120-125, 133-152`）。
- **存在的问题**：执行中途崩溃后重试会**重复执行已成功步骤**（副作用工具重复写业务数据）；失败计划无法恢复，用户只能重新发起。
- **为什么值得优化**：这是全项目最严重的业务正确性风险（重复创建沟通记录/评分记录），也是"Agent 工程化"面试最常被挖的点——执行确定性与幂等是 Agent 落地的核心难题。
- **建议技术方案**：
  1. 步骤执行前检查 `step.status`：COMPLETED/SKIPPED 的步骤直接采用历史结果，不重新调用工具；
  2. 引入**步骤幂等键**：`toolName + 规范化参数 hash`，执行前查 `agent_plan_step` 近期成功记录（同计划内），命中则复用结果；
  3. 失败计划支持"恢复重试"：新增 `POST /front/agent/chat` 已有的 `planId + confirmedToolNames` 通道复用时，若计划为 FAILED，允许带 `retry=true` 从第一个非 COMPLETED 步骤继续（而非新建计划）；
  4. 执行开始时把计划从 FAILED 重置为 PLANNED 并记录 `retry_count`；崩溃残留的 RUNNING 步骤在加载时按超时规则（如 >10 分钟）标记为 FAILED_RECOVERED 再重跑；
  5. `AgentToolExecutionResult` 增加 `idempotentHit` 标记，Trace 可区分"真实执行"与"幂等命中"。
- **主要修改模块/类**：`AgentPlanExecutorServiceImpl`、`AgentChatServiceImpl.chat`（planId 复用分支）、`AgentPlanStep` 实体（加 `execution_key`、`retry_count` 字段）、`AgentPlan`（加 `retry_count`）。
- **是否改数据库**：是（`agent_plan_step` + 1 字段，`agent_plan` + 1 字段；`sql/` 目录补第一份 DDL，见任务 4）。
- **是否新增依赖**：否。
- **是否新增测试**：是——必须补：① 步骤级幂等（模拟第 2 步崩溃后重试，断言第 1 步工具未被二次调用，用 Mockito 计数验证）；② 失败计划恢复；③ RUNNING 超时恢复。
- **预期验收标准**：单测证明"部分成功计划重试时，已 COMPLETED 步骤的工具调用次数为 0"；`agent_plan_step.execution_key` 唯一；人工场景：杀进程后重试，沟通记录表无重复行。
- **技术学习价值**：★★★★★（幂等设计、状态机、崩溃恢复——Java 后端面试硬通货）
- **面试展示价值**：★★★★★（"你的 Agent 如何保证副作用工具不重复执行？"这是高频深挖题）
- **实施复杂度**：★★（改动集中在 2 个类 + 2 个字段，逻辑清晰）
- **推荐优先级**：**1**

### 任务 2（P0）：RAG 真正接入 Agent 主链路并修复检索链路

- **当前实现**：预检索整套逻辑（`retrieveRagContext` + 降级 + Trace）是死代码（`AgentChatServiceImpl.java:553-679`，零调用）；RAG 只经 RagSearchTool 服务 GENERAL_CHAT；ivfflat 索引因 2048 维被跳过（`RagVectorStoreServiceImpl.java:104-109`）；无连接池、无 rerank、无查询改写。
- **存在的问题**：RAG 基础设施齐全但"最高价值的使用方式"没接线；简历写"RAG"会被一句"你的 RAG 在哪被调用"问穿。
- **为什么值得优化**：RAG 是 AI 应用实习面试核心考点；且激活的是**已经写好的高质量代码**（含防注入声明、引用、失败降级），边际成本低、叙事收益大。
- **建议技术方案**：
  1. **接线**：在 `chat()` 意图识别后、Planner 前调用已存在的 `retrieveRagContext`（按意图决定是否启用：GENERAL_CHAT + JOB_MATCH + RESUME_ANALYZE 启用，纯搜索类跳过），把 `ragEnhancedMessage` 传入总结阶段（这正是死代码 `buildRagEnhancedMessage` 设计的用途）；
  2. **修索引**：二选一——① 将 embedding 模型换成 ≤2000 维（配置化，`RagProperties.dimension` 已有）；② 或将 ivfflat 上限判断改为支持 HNSW（pgvector 0.5+ 支持 `vector_cosine_ops` hnsw，需 PG 版本确认）。目标：`EXPLAIN` 显示走向量索引；
  3. **失败降级接通**：RAG 失败走已写好的"不中断聊天 + ragStatus=FAILED Trace"路径（`:590-622`），不再拖垮计划；
  4. **补 rerank**：在现有规则重排（semantic×0.65+keyword×0.35）之上加一层轻量交叉打分——可用 embedding 余弦对 query 与 chunk 标题+首句重算，或接入一个 rerank API（新增依赖，可选）；
  5. **连接池**：`DriverManagerDataSource` → HikariCP（项目已有 HikariCP 依赖，零新增）；
  6. **查询改写（可选深化）**：总结场景前用 LLM 把用户口语化问题改写为检索式 query（走已有网关，新增一个 REWRITE 场景）。
- **主要修改模块/类**：`AgentChatServiceImpl`（接线 + 意图门控）、`RagVectorStoreServiceImpl`（索引策略）、`RagConfig`（连接池）、`RagRetrievalServiceImpl`（rerank）、`RagProperties`/yml（维度配置）。
- **是否改数据库**：否（向量表已存在；若换维度需重建 `job_knowledge` 表——注意这是破坏性操作，需备份）。
- **是否新增依赖**：否（rerank API 为可选项）。
- **是否新增测试**：是——检索服务单测（mock embedding + 内存向量集合，验证混合召回/权限过滤/rerank 排序）；接线后的 Agent 链路测试（mock RagRetrievalService，验证 RAG 失败时聊天不中断且 Trace 标记 FAILED）。
- **预期验收标准**：`chat()` 主流程 Trace 中稳定出现 RAG 事件（含 hitCount/references）；RAG 服务异常时聊天仍返回且 Trace `ragStatus=FAILED`；`EXPLAIN` 确认向量索引生效；Eval 数据集新增 10 条 RAG 用例，`ragHitRate` 有基线数字（不承诺具体值，以首轮运行结果为准）。
- **技术学习价值**：★★★★★（混合检索、rerank、降级、引用——RAG 全链路）
- **面试展示价值**：★★★★★
- **实施复杂度**：★★★（接线本身小；索引修复涉及数据重建，需谨慎）
- **推荐优先级**：**2**

### 任务 3（P0）：测试基座修复与主链路测试补强

- **当前实现**：43 个单测可运行且全过（本轮实测），但标准 `mvn test` 因 `AdminDashboardServiceImplTest.java:59` 构造器不匹配（11 参 vs 14 参）编译失败；0 个 `@SpringBootTest`；Executor/Guardrails/网关/主链路零覆盖；`Eat.java` 是无断言垃圾测试。
- **存在的问题**：简历写"单元测试覆盖"会被一句"`mvn test` 跑一下"当场戳破；没有回归安全网，后续所有优化都在裸奔。
- **为什么值得优化**：这是所有后续任务的地基——先修基座，再动业务；且成本极低（第一个修复只是补 3 个参数）。
- **建议技术方案**：
  1. **修编译**：更新 `AdminDashboardServiceImplTest` 构造器参数（补 `applicationRecordMapper/reminderMapper/workflowTaskMapper` 三个 mock）；删除 `Eat.java`；给 `ExternalConnectorIntegrationTest` 的 Bootstrap pom 补 `hutool-all` test 作用域依赖或改为不依赖 hutool 的断言；
  2. **补主链路单测（Mockito，不起 Spring）**：
     - `AgentPlanExecutorServiceImplTest`：步骤循环/失败终止/计划级幂等/（任务 1 完成后）步骤级幂等；
     - `AgentToolInvokerTest`：switch 分发/未知工具抛错/输出校验失败转 failed；
     - `SensitiveOperationGuardTest` + `ToolAccessGuardTest`：确认拦截/越权拦截/上下文缺失拦截；
     - `HallucinationGuardTest`：执行失败但声称成功 → 替换；无成功步骤但声称"根据你的简历" → 加提示；
     - `AgentIntentRouterTest`：7 类意图关键词覆盖（含"复盘"先于"面试"的顺序回归）；
  3. **补 2 个 `@SpringBootTest` 冒烟测试**（用 H2 或 Testcontainers-MySQL，二选一；注意项目用 MyBatis-Plus + 全局逻辑删除配置）：① 上下文加载 + Mapper 扫描 + 健康检查；② `AgentController` 登录态 mock（sa-token 有测试模式）下的 chat 冒烟（mock 模型网关，不走真实 LLM——**绝不调用收费模型**）；
  4. **CI 钩子（可选）**：GitHub Actions 跑 `mvn test`，与任务 10（Eval 异步化）合并做质量门禁。
- **主要修改模块/类**：`src/test/` 新增 5-6 个测试类 + 修 2 个；`Job-Agent-Bootstrap/pom.xml`（test 依赖）。
- **是否改数据库**：否（集成测试用 H2/Testcontainers，不动开发库）。
- **是否新增依赖**：是（H2 或 testcontainers，二选一）。
- **是否新增测试**：本身即是。
- **预期验收标准**：`mvn test` 全绿（可当场演示）；主链路核心类单测覆盖率（行覆盖）从当前水平提升并给出 JaCoCo 报告数字；冒烟测试不依赖真实模型凭证。
- **技术学习价值**：★★★（测试策略本身是 Java 工程必修课）
- **面试展示价值**：★★★★★（"你的项目怎么保证质量"必答）
- **实施复杂度**：★（修复）+ ★★（补测试）
- **推荐优先级**：**3**

### 任务 4（P1）：数据库 Schema 版本化（sql/ 目录落地）

- **当前实现**：`sql/` 空目录；约 30 张表只在开发者本地；pgvector 表运行时 DDL。
- **问题**：新环境不可复现；索引不可审计；实体与表结构漂移无防护。
- **方案**：按模块输出基线 DDL（`sql/01_schema_core.sql` 业务表、`02_schema_agent.sql` Agent 表、`03_schema_ai.sql` 模型/Prompt/Eval 表、`04_schema_rag.sql` MySQL 侧 RAG 表 + pgvector 建表脚本）；用 Flyway 或 Litestream 式版本管理（推荐 Flyway，新增依赖一个 starter）；随后所有加字段（任务 1 的 `execution_key` 等）走迁移脚本。
- **改数据库**：是（仅新增脚本，不改动现有库）。**复杂度**：★。**优先级**：建议与任务 1 同周做（任务 1 要加字段）。

### 任务 5（P1）：Planner 升级——"规则意图 + LLM 兜底 + 模板 + LLM 补步"

- **当前实现**：`AgentIntentRouter` 纯关键词；`AgentPlanTemplateFactory` 硬编码 8 模板；`AgentPlanParameterExtractor` 正则。
- **问题**：词表外表达全部落空；复杂多步任务无法表达；面试称"自主规划"会穿帮。
- **方案（三阶段渐进，每阶段独立上线）**：
  1. **阶段一（意图层）**：规则未命中或置信度低时，走网关一个 `INTENT_CLASSIFY` 场景，输出 JSON `{intentCode, confidence, extractedParams}`，规则优先、LLM 兜底（注释里已规划：`AgentIntentRouter.java:14-15`）；
  2. **阶段二（参数层）**：`AgentPlanParameterExtractor` 增加 LLM 抽取通道（`PARAM_EXTRACT` 场景，输出受 Schema 约束的 JSON），与正则结果合并（正则优先，LLM 补缺）——复用 `AgentMemoryLlmExtractor` 已有的"关键词预筛 + 网关调用 + JSON 解析 + 失败降级"模式；
  3. **阶段三（计划层，可选）**：模板步骤之外，允许 LLM 基于目标生成**附加步骤**（仍受 `AgentToolSchemaRegistry` 白名单约束，只能选已注册工具），形成"模板主干 + LLM 补步"的混合计划；步骤间数据依赖用 `{{step_N.output.xxx}}` 占位符在 Executor 里替换（这同时补上 Executor 的数据传递缺口）。
- **Guardrails 兼容**：白名单模型不变（LLM 只能从 Schema 注册表选工具），`ToolAccessGuard` 的校验逻辑天然兼容——这是重要卖点："我们没有放弃确定性安全边界"。
- **改数据库**：否（`ai_model_route` 加场景配置行即可）。**新增依赖**：否。**测试**：意图分类准确率进 Eval（新增 expected_intent 断言已有支持）。
- **学习价值**：★★★★★ **面试价值**：★★★★★ **复杂度**：★★★（阶段三较高）。
- **优先级**：A 主线（可靠性）完成后启动，阶段一/二先行。

### 任务 6（P1）：Context Builder——多轮历史、Token 预算与摘要压缩

- **当前实现**：总结提示词只含当前轮输入（`AgentChatServiceImpl.java:988-1049`）；预算靠字符截断；无历史回灌、无摘要。
- **方案**：
  1. 新建 `AgentContextBuilder` 组件，统一组装：System Prompt（网关版本化）+ 记忆画像 + 召回记忆 + **最近 N 轮历史**（从 `ai_message` 加载，默认 N=6，可配）+ 计划/执行结果 + RAG 知识；
  2. 历史消息裁剪策略：条数窗口 + 单条长度截断 + **超预算时滚动摘要**（用网关 `CONTEXT_SUMMARY` 场景把更早历史压成一段摘要，缓存到 `ai_conversation.summary` 字段——加 1 个字段）；
  3. Token 预算管理：按场景配置总预算（总结场景建议 ≤4000 tokens），分配比例为 历史 30% / 记忆 20% / 执行结果 40% / 其他 10%，超限按优先级丢弃（先丢历史细节，再截断工具结果预览长度——预览长度从 1000 字符改为可配置）；
  4. 敏感信息过滤：历史入库前已脱敏，Builder 侧对工具结果 JSON 再过一次 `maskSensitiveData`。
- **改数据库**：是（`ai_conversation` 加 `summary`/`summary_upto_message_id`）。**新增依赖**：否。**测试**：Builder 单测（给定超长历史/记忆/结果，断言预算内且关键信息保留）。
- **学习价值**：★★★★（上下文工程是 2025-2026  Agent 面试热词）**面试价值**：★★★★ **复杂度**：★★★。
- **优先级**：与任务 5 阶段二并行。

### 任务 7（P1）：Workflow 两个 P0 修复（轮询间隔 + 邮件幂等）

- **当前实现**：`POLL_FIXED_DELAY_MS = 60*60*1000L`（`WorkflowTaskScheduler.java:25`）；邮件 handler 每次执行都发信（`InterviewEmailNotifyWorkflowTaskHandler.java:92`）。
- **方案**：① 轮询间隔改配置化，默认 5-10 秒（`job.workflow.poll-interval-ms`），恢复间隔可保留；② 邮件幂等：发送前查 `agent_workflow_task_log`/业务表是否已成功发送过同一 `bizId+template`，或利用任务的 `retry_count` 与 `agent_daily_report_record.email_status` 联动——推荐"发送记录表/状态位"方案（加 `agent_workflow_task.idempotency_key` 唯一索引，执行前 INSERT … ON DUPLICATE 抢占执行权）；③ 顺手把串行执行改为受控并发（有界线程池，`POLL_LIMIT` 上调）——注意这会引入 `AgentRuntimeContext` 的 ThreadLocal 传递问题，建议**最后做且单独上线**。
- **改数据库**：是（`agent_workflow_task` 加 `idempotency_key`）。**测试**：重复执行 handler 断言只发一次信（mock MailSender 计数）。
- **学习价值**：★★★ **面试价值**：★★★★（"定时任务重复消费怎么办"经典题）**复杂度**：★★。
- **优先级**：建议与任务 3 同周顺手修掉（两个修复各几十行）。

### 任务 8（P2）：模型网关稳定性补强——限流、退避重试、HALF_OPEN、统计 SQL 化

- **当前实现**：无限流；立即重试无退避（`AiModelGatewayServiceImpl.java:148-195`）；两态熔断（`:496-581`）；成本统计全表拉内存聚合（`AdminAiModelServiceImpl.java:300-322`）。
- **方案**：① 加**信号量/令牌桶限流**（按 sceneCode + 按用户双维度，用 Guava RateLimiter 或手写令牌桶——项目已有 Guava 依赖，零新增）；② 重试改指数退避 + 抖动（`base 500ms × 2^attempt ± jitter`），仅对 429/5xx/超时重试，4xx 业务错误不重试；③ 熔断加 HALF_OPEN：冷却后放 1 个探测请求，成功则关闭；④ 成本统计改 SQL `GROUP BY` + 分页（Dashboard 同改）；⑤ 网关补单测（用 MockWebServer/ WireMock mock OpenAI 兼容端点，验证重试/熔断/限流/成本计算——**不调用真实收费 API**）。
- **改数据库**：否。**新增依赖**：WireMock（test）可选。
- **学习价值**：★★★★（稳定性工程是 Java 后端立身之本）**面试价值**：★★★★ **复杂度**：★★★。
- **优先级**：B 主线期间。

### 任务 9（P2）：Memory 治理——TTL、冲突解决、语义召回、用户自助

- **当前实现**：无 TTL；`conflictDetected` 只记录；召回是词重叠打分；读引发写放大（`AgentMemoryServiceImpl.java:557-568`）；无前台入口。
- **方案**：① `agent_long_term_memory` 加 `expire_time`，召回时过滤 + 每日定时归档过期（复用已有 scheduler 模式）；② 冲突策略：按 sourceType 置信度排序决定胜出者（USER_EXPLICIT > LLM_EXTRACT > TOOL_RESULT），冲突时保留旧值并标记待确认，而不是静默覆盖；③ 语义召回：对 `memoryValue` 生成 embedding 存 pgvector（复用 RAG 的 embedding 基建！——这是个漂亮的复用点），召回 = 语义 topK + 词重叠重排；④ `touchLastUsedTime` 改为异步/批量（或只更新独立的 `last_used_time` 列且不参与 `updateTime` 排序——改排序键为 `importance, create_time`）；⑤ 前台加"记忆管理"页（用户可查看/删除自己的记忆）——**注意：用户要求不堆业务按钮，此项仅在前端已具备能力时做，否则只做 API**。
- **改数据库**：是（1-2 个字段）。**新增依赖**：否。
- **学习价值**：★★★★ **面试价值**：★★★★（"记忆冲突/过期怎么处理"是深度题）**复杂度**：★★★。
- **优先级**：B 主线后期。

### 任务 10（P2）：Eval 异步化与质量门禁

- **当前实现**：手工触发、同步执行（`AgentEvalServiceImpl.java:266-283`）；工具名 contains 匹配；时间窗 trace 提取。
- **方案**：① `runDataset/runAll` 改为创建 `agent_eval_run` 后投递到 Workflow 任务（已有 `EVAL_DATASET_REGRESSION` taskType！接线即可），前台轮询进度；② 匹配规则强化：工具名改**精确 equals**（别名解析后）、trace 提取改按 `conversation_id`（executeCase 时用独立 conversation 隔离并发）；③ 新增 GitHub Action：每日跑核心数据集，passRate 相对基线下降超过阈值即 fail——"Eval 防退化"从理念变成机制；④ Eval 主链路补单测（mock AgentChatService）。
- **改数据库**：否。**测试**：匹配规则单测。
- **学习价值**：★★★ **面试价值**：★★★★（"你怎么发现 Agent 改坏了"）**复杂度**：★★。
- **优先级**：任务 5/6 落地后必须做（没有 Eval 的优化是盲改）。

### 任务 11（P2）：名实相符工程——Infra-Ai 与 MCP-Tools 二选一

- **当前实现**：Infra-Ai 5 个 Assistant + Config 死代码（`legacy-enabled=false` + 零调用）；MCP-Tools 6 个 PREVIEW 桩；`job.mcp` 死配置。
- **方案（每条二选一，推荐组合如下）**：
  - **Infra-Ai → 选"正名+最小激活"**：删除 `legacy-enabled` 开关叙事，在 README 和 javadoc 注明"主链路使用自研网关；LangChain4j AiServices 层为历史迁移遗留，默认关闭"；**或**把 `JobAgentSummaryAssistant` 真正激活为总结场景的一个可选实现（网关路由加一个 `USE_LANGCHAIN4J` 开关）——推荐前者（删除成本 > 维护成本），若想保留 LangChain4j 叙事则选后者并补测试；
  - **MCP-Tools → 选"正名"**：把模块重命名或至少在 README/类注释明确"连接器预览桩，非 MCP 协议"；**或**真正实现一个 MCP client（引入 `langchain4j-mcp`，把 filesystem server 的 `job.mcp` 配置接通，做一个真实工具调用演示）——后者学习价值高但优先级低，建议放到全部 P0/P1 之后；
  - **Redis/SSE**：要么补 `spring-boot-starter-data-redis` 并把 sa-token 会话迁 Redis（解决重启丢会话），要么从 pom 注释和 yml 中移除宣称；SSE 若不做流式，删掉 `langchain4j-reactor` 依赖或在 roadmap 留位（流式输出本身是个好的独立任务：`SseEmitter` + 网关流式回调，面试亮点，但复杂度 ★★★，建议放最后）。
- **复杂度**：★（正名）～ ★★★（真 MCP/真 SSE）。**优先级**：正名类立即做；激活类排后。

### 任务 12（P3）：安全与配置治理

- **当前实现**：dev.yml 明文凭证入库；`agent_trace_log.errorMsg` 未脱敏；`StdOutImpl` 全量 SQL 日志；PiiMasker 有盲区。
- **方案**：① 凭证改环境变量注入（dev.yml 用 `${DB_PASSWORD:}`），已入库的明文口令**轮换**（重要：仅改配置不够，泄露过的密钥必须轮换）；② `AgentTraceServiceImpl.saveTrace` 的 errorMsg 过 `maskText`，与 Observation 对齐；③ 生产 profile 关闭 SQL stdout 日志；④ PiiMasker 补 `Bearer/accessKey/credential` 模式；⑤ `ai_model_config.api_key` 落库加密（JCE AES + 配置密钥，或至少 Base64+备注"生产应接 KMS"）。
- **复杂度**：★★。**优先级**：与任务 4 同周。

---

## 3. 推荐的前三项优化任务（详细方案）

综合排序：**任务 1（Executor 幂等与恢复）→ 任务 3（测试基座）→ 任务 2（RAG 主链路接入）**。

> 排序说明：把"测试基座"排在 RAG 之前，是因为任务 1 会改动执行核心，没有测试安全网属于裸奔；基座修复本身只要 1-2 天，却能让后续所有改动可验证、可回归。若更看重"简历叙事速度"，可将 3 与 2 对调——两者无强依赖。

---

### 3.1 任务一：Executor 步骤级幂等与失败恢复执行

#### 为什么推荐

当前问题（审计编号 #2/#3）：Executor 只有计划级幂等，执行中途崩溃后重试会重复调用已成功步骤的工具——`GreetingGenerateTool` 会**重复创建沟通记录**、`ResumeAnalyzeTool` 会重复写评分记录；且 FAILED 计划被幂等逻辑永久挡回，用户感知"重试无效"。这是业务正确性问题，不是性能问题。现在解决最合适，因为：① 它是后续所有执行层优化（超时、重试、并发）的地基；② 修复窗口小（2 个类 + 2 个字段）；③ 面试命中率极高。

#### 具体改造范围

| 类型 | 文件 | 改动 |
|---|---|---|
| Java 主类 | `service/impl/AgentPlanExecutorServiceImpl.java` | `executePlan` 主循环：步骤执行前状态检查；新增 `resolveStepIdempotency`；FAILED 计划恢复分支；RUNNING 超时判定 |
| Java 主类 | `service/impl/AgentChatServiceImpl.java` | `chat()` 中 planId 复用分支：透传恢复意图（如 `confirmedToolNames` 非空且计划 FAILED 时允许恢复，而不是新建计划） |
| Java 主类 | `agent/executor/AgentToolExecutionResult.java` | 加 `idempotentHit` 字段 |
| 实体 | `Job-Agent-Framework/.../entity/agent/AgentPlanStep.java`、`AgentPlan.java` | 加 `execution_key`（varchar 128，计划内唯一索引）、`retry_count` |
| Mapper | `AgentPlanStepMapper` | 加按 `execution_key` 查询方法 |
| SQL | `sql/05_migration_executor_idempotency.sql`（随任务 4 建立） | ALTER + 唯一索引 + 回填脚本 |
| 测试 | `src/test/.../AgentPlanExecutorServiceImplTest.java`（新增） | 3 个核心用例（见下） |

#### 技术实现方案（核心设计）

1. **步骤执行键**：`execution_key = planId + ":" + stepNo + ":" + toolName + ":" + sha256(规范化参数JSON)`。规范化 = 参数 Map 按 key 排序序列化（保证同语义参数同键）。
2. **执行前检查**（`executePlan` 循环内，替代无条件执行）：
   ```
   if (step.status == COMPLETED) { 复用历史结果，标记 idempotentHit=true，跳过调用 }
   if (step.status == RUNNING && step.updateTime > now-10min) { 抛并发冲突（另一线程在执行） }
   if (step.status == RUNNING && 超时) { 标记 FAILED 后允许重跑 }
   ```
3. **幂等抢占**（防并发双击）：执行真正工具前，`UPDATE agent_plan_step SET status='RUNNING', execution_key=? WHERE id=? AND status IN ('PENDING','FAILED')`，影响行数 0 说明被别人抢先 → 直接走历史结果分支。
4. **失败恢复**：`AgentChatServiceImpl` 复用计划时，若计划 FAILED 且用户再次提交（带或不带 planId），走 `executePlan(userId, planId, recover=true)`：计划重置 PLANNED、`retry_count+1`、从第一个非 COMPLETED 步骤继续；`retry_count >= 3` 时拒绝并提示人工介入。
5. **Trace 区分**：`AgentObservationRecord` 的 EXECUTOR 事件 `eventName` 后缀 `[IDEMPOTENT_HIT]`，后台可统计真实执行 vs 命中比例——这本身是个好的面试数字。

#### 对现有系统的影响与兼容

- 影响面：仅 Agent 计划执行路径；业务 Service 无感；
- 兼容：`execution_key` 可空，老数据按 PENDING 处理；恢复入口默认关闭（显式 `recover=true` 才启用），不影响现有"失败→重新发起"行为；
- 回滚：删字段 + 还原两个类即可（建议 git 独立分支/commit）。

#### 验收方法

1. **单元测试**：Mockito mock 工具协作者，验证"第 1 步成功、第 2 步抛异常后，以 recover 模式重跑，第 1 步工具交互次数 = 1（首次），第 2 步 = 2（首次+重试）"；
2. **集成/手工**：本地启动后，在 GreetingGenerateTool 执行中 kill 进程 → 重启 → 同会话重试 → 断言 `job_communication_record` 表该 plan 相关记录只有 1 条；
3. **可观测**：Admin Observation 页面能看到 `[IDEMPOTENT_HIT]` 事件。

#### 面试价值（完成后应能回答）

- "Agent 执行副作用工具，网络超时了怎么办？重试会不会重复下单/重复发信？"——幂等键设计、状态机抢占、崩溃恢复；
- "幂等键怎么选？为什么用参数 hash 而不是业务 ID？"——参数 hash 覆盖"同计划同步骤同参数"，业务 ID 在 Agent 场景不总存在；
- "你的执行状态机和数据库事务怎么配合？"——步骤状态先落库（RUNNING 抢占）再执行，执行结果后落库，长事务拆成短事务。

#### 是否推荐立即实施：**是，第一优先。**

---

### 3.2 任务二：测试基座修复与主链路测试补强

#### 为什么推荐

`mvn test` 当前编译失败（一个过期文件阻断 24 个测试文件）——这意味着"测试全部通过"这句话目前是假的（虽然 43 个用例单独跑全过，本轮已实测）。同时 Executor/Guardrails/网关这些最需要测试的模块零覆盖，而任务 1 就要动 Executor。基座不修，后续优化没有安全网；基座修了，"质量意识"本身就是面试叙事。

#### 具体改造范围

| 文件 | 改动 |
|---|---|
| `src/test/.../AdminDashboardServiceImplTest.java` | 构造器补 3 个 mock 参数（applicationRecordMapper/reminderMapper/workflowTaskMapper），断言同步更新（当前断言 metrics hasSize(4)，需按 14 参后的实际行为核对） |
| `src/test/java/com/job/test/Eat.java` | 删除 |
| `src/test/.../ExternalConnectorIntegrationTest.java` | Bootstrap pom 补 hutool test 依赖，或去掉 hutool 用法 |
| 新增 `AgentPlanExecutorServiceImplTest` | 步骤循环/失败终止/幂等 |
| 新增 `AgentToolInvokerTest` | 分发/未知工具/输出校验 |
| 新增 `SensitiveOperationGuardTest`、`ToolAccessGuardTest` | 拦截路径 |
| 新增 `HallucinationGuardTest` | 成功声称替换/依据不足提示 |
| 新增 `AgentIntentRouterTest` | 意图顺序回归（"复盘模拟面试"必须命中 MOCK_INTERVIEW 而非 INTERVIEW_PREPARE） |
| 新增 `AgentChatIntegrationSmokeTest`（@SpringBootTest） | 上下文加载 + chat 冒烟（mock AiModelGatewayService，**不调用真实模型**） |
| `pom.xml` | H2 或 testcontainers（集成测试二选一）；JaCoCo 插件（覆盖率报告） |

#### 技术实现方案

- 单测策略：**不起 Spring**，直接 new + Mockito（与现有 8 个 Mockito 测试风格一致，学习曲线低）；
- `AgentPlanExecutorServiceImpl` 可测试性：当前构造器注入齐全（mapper/invoker/guardrail/observation…），直接 mock 即可，无需重构——这是现有设计的好处；
- 冒烟测试：`@SpringBootTest` + `@AutoConfigureMockMvc` + sa-token 测试登录（`StpUtil.setLoginId` 在测试线程可用），`AiModelGatewayService` 用 `@MockitoBean` 替换；H2 需要处理 MySQL 特定 SQL——**建议用 Testcontainers-MySQL**（与开发库一致，避免 H2 方言假通过），若环境不允许 Docker 则退化为"仅上下文加载 + Mapper 扫描"的纯装配测试并在报告注明环境限制；
- JaCoCo：首次运行建立基线数字（不设门禁，先看见）。

#### 对现有系统的影响

仅测试代码 + pom；对主代码零风险。唯一注意：冒烟测试需要数据库——本地开发库即可（只读查询），或用 Testcontainers 隔离。

#### 验收方法

- `mvn test` **全绿**（可当场演示，这是最直接的面试证据）；
- JaCoCo 报告：`agent/executor`、`agent/guardrail`、`agent/plan` 包行覆盖率有明确数字（目标：执行器与护栏 > 70%，以实际为准）；
- CI（可选）：push 即跑。

#### 面试价值

- "你的项目测试分几层？为什么没有集成测试？"——现在可以答：纯逻辑单测 + 装配冒烟 + 计划中的容器化集成；
- "Agent 链路怎么 mock 模型？"——网关接口 mock，测试不产生 token 费用，可反复跑；
- "你怎么防测试退化？"——CI + 每轮优化配测试（本路线图每项任务都带测试要求）。

#### 是否推荐立即实施：**是，与任务一并行或紧随其后（1-2 天量级）。**

---

### 3.3 任务三：RAG 真正接入 Agent 主链路并修复检索链路

#### 为什么推荐

RAG 是 AI 应用实习面试的必考模块，而项目现状是"基础设施齐全、主链路没接线"：`retrieveRagContext` 整套高质量逻辑（含引用、防注入声明、失败降级、Trace）是死代码，RAG 实际只服务闲聊意图，且向量索引因维度超限没建。激活它，等于用几行接线代码把一个"写好了但没用"的模块变成简历主线，同时修复的是真实性能隐患（全表扫描）。

#### 具体改造范围

| 文件 | 改动 |
|---|---|---|
| `service/impl/AgentChatServiceImpl.java` | `chat()` 意图识别后调用 `retrieveRagContext`（意图门控）；`ragEnhancedMessage` 传入总结变量；RAG 事件接入 Observation |
| `agent/intent/AgentIntentRouter.java` 或新建 `RagActivationPolicy` | 定义哪些意图启用预检索（GENERAL_CHAT/JOB_MATCH/RESUME_ANALYZE 启用；JOB_SEARCH/INTERVIEW_* 跳过） |
| `rag/service/impl/RagVectorStoreServiceImpl.java` | 索引策略：支持 HNSW 或降维到 ≤2000；`CREATE INDEX` 逻辑加日志断言 |
| `rag/config/RagConfig.java` | `DriverManagerDataSource` → HikariCP |
| `rag/service/impl/RagRetrievalServiceImpl.java` | 叠加 rerank（query vs 标题+首句交叉打分） |
| `application-local.yml` / `RagProperties` | 维度配置化（若降维需重建向量表——**破坏性操作，先备份**） |
| 测试 | `RagRetrievalServiceTest`（mock embedding/向量存储：混合召回、权限过滤、rerank 排序）；`AgentChatRagFallbackTest`（RAG 抛异常 → 聊天不中断 + Trace ragStatus=FAILED） |

#### 技术实现方案

1. **接线点**（`chat()` 第 4 步意图识别后、Planner 前）：
   ```java
   RagContext ragContext = ragActivationPolicy.enabledFor(intentCode)
       ? retrieveRagContext(userId, conversation.getId(), intentCode.name(), safeMessage)
       : RagContext.disabled();
   ```
   然后 `ragContext.getEnhancedMessage()` 作为新变量注入总结提示词（`buildExecutorSummaryVariables` 加 `ragContextMessage`）——注意 ragContext 也要参与 Planner 后的总结，所以保存到方法级变量。
2. **索引修复二选一**：优先换 ≤2000 维 embedding 模型（配置化，代码已有维度强校验 `RagEmbeddingServiceImpl.java:275-292`）；PG 版本 ≥ 0.5 则改 HNSW（`USING hnsw (embedding vector_cosine_ops)`），突破 2000 维限制。改完后用 `EXPLAIN` 验证索引扫描。
3. **失败降级**：活路径已由 `retrieveRagContext` 的 try-catch 覆盖（`:590-622`），接线后自动生效——RAG 失败不再拖垮计划，Trace 标记 FAILED。
4. **rerank**：保持现有规则分为基础分，追加 `0.2 × cosine(queryEmbedding, titlePlusFirstSentenceEmbedding)`——标题/首句 embedding 在索引时预算并落 `rag_chunk.metadata_json`（不加表）。
5. **向量表重建纪律**：降维属于破坏性操作——必须先 `pg_dump` 备份 `job_knowledge`，写好重建脚本（删表→按新维度建表→全量重灌，`RagIndexServiceImpl` 已有全量重建入口），并在报告记录操作步骤。

#### 对现有系统的影响

- GENERAL_CHAT 计划内的 RagSearchTool 与预检索会**重复召回**——需要决策：预检索启用后，从 generalChatPlan 模板中移除 RagSearchTool 步骤（推荐，避免双倍 embedding 成本），或让预检索只服务业务意图。**推荐前者**，同时这简化了"RAG 失败拖垮计划"问题；
- 模型网关多一个可选的 REWRITE 场景调用（若做查询改写）——注意成本，默认关闭；
- 无关功能（面试准备、模拟面试等）零影响。

#### 验收方法

1. 主 Trace 中稳定出现 RAG 事件（hitCount/references 非空时有引用编号）；
2. RAG 服务不可用时（停 pgvector 模拟），聊天正常返回且 Trace `ragStatus=FAILED`；
3. `EXPLAIN` 显示 `job_knowledge` 走向量索引；
4. Eval 数据集新增 10 条 RAG 用例（`expected_rag_document_id` 已有字段支持），跑出首轮 `ragHitRate` 基线——**只记录基线，不承诺目标值**；
5. 连接池验证：并发 20 次检索无连接泄漏（HikariCP 指标）。

#### 面试价值

- "RAG 全链路你怎么做的？chunk 策略？混合检索？rerank？"——全部有代码可讲；
- "RAG 失败了怎么办？"——降级 + Trace（多数候选人答不上来）；
- "向量索引怎么选？ivfflat 和 HNSW 区别？"——有踩坑实证（2048 维跳过索引）可讲，这是真实的工程细节；
- "怎么证明 RAG 有效果？"——Eval ragHitRate 基线 + 引用率（诚实的"尚无证据→有基线"叙事）。

#### 是否推荐立即实施：**是，但排在测试基座之后（它涉及数据重建，需要测试网兜底）。**

---

## 4. 推荐开发先后顺序（时间线）

```
第 1 周：任务 3（测试基座，1-2 天）+ 任务 4（SQL 版本化，0.5 天）+ 任务 7（Workflow 两个 P0，1 天）+ 任务 12 凭证治理（0.5 天）
        → 里程碑：mvn test 全绿；sql/ 有基线；两个 P0 业务风险出清
第 2-3 周：任务 1（Executor 幂等与恢复，含测试）
        → 里程碑：重复执行风险出清；Observation 有 [IDEMPOTENT_HIT] 可观测
第 4-5 周：任务 2（RAG 接入 + 索引修复 + rerank，含 Eval RAG 用例基线）
        → 里程碑：RAG 进主链路；向量索引生效
第 6-7 周：任务 5 阶段一/二（意图 LLM 兜底 + 参数 LLM 抽取）+ 任务 10（Eval 异步化门禁）
        → 里程碑：规划开始有模型参与；Eval 可自动回归
第 8-9 周：任务 6（Context Builder）+ 任务 8（网关限流/退避）
第 10 周+：任务 9（Memory 治理）、任务 11（名实相符/真 MCP/SSE 流式）、任务 5 阶段三（LLM 补步）
```

> 节奏说明：每周可投入时间未知，以上按"全职投入"估算；学生/实习求职者按周拆分即可，关键是**每完成一个任务就更新 README 与简历条目**，不要攒到最后。

---

## 5. 面试技术亮点提升路线

| 时间点 | 简历可写的新亮点（有证据后） |
|---|---|
| 现在 | 模板化 Plan-and-Execute Agent 闭环；分层 Guardrails（服务端强制确认）；长期记忆闭环；自研模型网关（路由/主备/熔断/成本）；版本化 Prompt 运行时；Trace/Eval 平台 |
| 第 1 周后 | "+ 测试体系：43+ 单测全绿（mvn test 可演示）、装配冒烟、JaCoCo 覆盖率报告；数据库 Schema 版本化管理（Flyway）" |
| 第 3 周后 | "+ 执行可靠性：步骤级幂等键设计、崩溃恢复执行、幂等命中可观测（Agent 不重复执行副作用工具）" |
| 第 5 周后 | "+ RAG 全链路接入 Agent 主循环：混合检索、规则+语义 rerank、失败降级、引用溯源、Eval ragHitRate 基线；pgvector 索引选型实战（ivfflat 维度限制 → HNSW）" |
| 第 7 周后 | "+ 规划能力升级：规则 + LLM 兜底意图分类、LLM 参数抽取；Eval 驱动的质量门禁（CI 防退化）" |
| 第 9 周后 | "+ 上下文工程：多轮历史窗口 + 滚动摘要 + Token 预算分配；模型网关限流/退避/HALF_OPEN" |
| 收官 | "+ 记忆治理：TTL/冲突解决/语义召回（复用 RAG embedding 基建）；可选：真实 MCP 工具调用或 SSE 流式对话" |

**叙事建议**：面试时主动讲"审计 → 发现 → 修复 → 验证"的故事线（本次审计报告就是故事的开篇），比堆功能更有说服力。例如："我对自己项目做了一次代码级审计，发现 Executor 缺步骤级幂等，可能导致副作用工具重复执行，我设计了幂等键 + 状态抢占 + 崩溃恢复……"——这展示的不是搭功能，而是**工程判断力**。

---

## 6. 推荐补充学习的 Agent 技术知识

按与本项目的相关度排序：

1. **ReAct / Plan-and-Execute / Reflection 原始论文与变体**（本项目是模板化 PE，升级前必须理解动态版差异）；重点看 Replan 触发条件设计；
2. **LangChain4j 深入**：AiServices 工具调用循环、`ChatMemoryProvider`/`MessageWindowChatMemory`、`@Tool`/`@P` 的参数 Schema 生成、结构化输出（`JsonSchemas`）——本项目 Infra-Ai 已铺路但未激活，学习成本低；
3. **上下文工程**：token 预算分配、滚动摘要、历史裁剪策略（本项目任务 6 直接练）；
4. **RAG 进阶**：chunk 策略（语义/结构化 vs 定长）、HyDE、多路召回 + RRF、cross-encoder rerank、评估指标（hit rate/MRR/上下文精确率）；
5. **Agent 可靠性**：幂等设计模式、Saga/补偿事务（副作用工具的"撤销"思维）、超时-重试-熔断三件套在 Agent 场景的特殊性（重试可能放大副作用）；
6. **LLM 评估**：LLM-as-Judge 的偏差与校准、数据集构建（从真实流量采样）、回归门禁设计；
7. **MCP 协议**（若做任务 11 激活路线）：JSON-RPC、tools/list/tools/call、stdio/SSE 传输、`langchain4j-mcp`；
8. **安全**：OWASP LLM Top 10（本项目已覆盖 LLM01/LLM02/LLM05/LLM06 部分）、间接注入（RAG 内容携带指令——本项目的 RAG 提示词已有防注入声明，可深化）；
9. **Java 侧**：Resilience4j（熔断/限流标准实现，对照自研网关）、Spring `@Async` + `TaskExecutor` 的上下文传递（TransmittableThreadLocal）、Testcontainers（集成测试）。

---

*本路线图与 `docs/agent-core-audit.md` 配套使用。所有任务均设计为可由后续逐项独立实施；实施任何一项前，建议先读审计报告对应章节确认现状未变化。*
