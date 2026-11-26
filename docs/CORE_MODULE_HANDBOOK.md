# Job-Agent 核心模块手册（复习 + 手写练习）

> 用途：一份能"照着读、照着敲"的代码地图。每个模块给出**职责 → 关键类（真实路径）→ 核心逻辑 → 代码节选 → 手写练习**。
> 代码节选为真实实现（为便于阅读做了少量删减），路径以当前 `wordbuddy-stage` 分支为准。

---

## 0. 怎么用这份手册

1. **第一遍**：只看每节的「职责 / 调用链 / 一张图」，建立全局感；
2. **第二遍**：对着「代码节选」在 IDE 里打开原文件，逐行读注释；
3. **第三遍**：合上代码，按第 13 节「手写练习」从 P1 开始默写，写完再对照原实现；
4. 每个练习都有**验收标准**，写完能自测，不靠"感觉会了"。

---

## 1. 全局：一次复合请求的完整链路

```
POST /front/agent/chat/stream  (satoken)
  │
  ├─ AgentController                    HTTP/SSE 协议、任务状态/取消接口
  │    └─ AgentChatService.streamChat  ← 薄入口（同步 chat 共用同一套路由）
  │         ├─ 护栏 / 意图识别（规则 → LLM 兜底）
  │         ├─ ChatRequestRouter       已有任务按 runtimeMode；新任务按意图+关键词+开关
  │         │
  │         ├─ MULTI_AGENT → JobSeekReactEntry
  │         │      └─ JobSeekWorkflow.execute
  │         │            PREPARE（SeekContextResolver：条件合并 + 简历定位）
  │         │            → 画像 ∥ 搜索（有界线程池，策略：条件独立才并行）
  │         │            → 各自成功即存产物（SeekArtifactService，版本由 SeekVersionPolicy 决定）
  │         │            → 搜索为空直接返回（不编造匹配）
  │         │            → MatchAgentNode → 校验报告 → SeekAnswerAssembler 组装答复
  │         │
  │         ├─ RESUME      → ResumeReactEntry → ResumeAgentNode
  │         ├─ REACT_SEARCH→ JobSearchReactEntry → JobSearchWorkflow
  │         └─ LEGACY      → AgentPlanExecutorServiceImpl（旧模板链路，仍在服务历史任务）
  │
  └─ 每个节点：ReActRunner（反馈循环）→ AgentToolInvoker（工具边界）→ 业务 Tool
        ↑ 预算：TaskExecutionBudget（父任务共享）  ↑ 进度：AgentProgressEvent（SSE）
```

**三条不变式**（所有模块都围绕它们）：

| 不变式 | 落地位置 |
|---|---|
| 身份只能来自后端认证，模型不能伪造 userId | `AgentToolInvoker` + `AgentRuntimeContext` |
| 模型只在白名单工具与预算内决策，结论必须走终结工具 | `ReActRunner` + `TerminalContract` |
| 任务状态只允许条件更新，取消不可被迟到结果覆盖 | `AgentTaskService.claimForExecution/completeExecution` |

---

## 2. 对话入口与路由

**职责**：决定"这条消息交给谁"，并保证同步/流式只有一套判断。

**关键类**
- `agent/application/routing/ChatRequestRouter.java`
- `agent/application/chat/service/ConversationService.java`
- `agent/application/chat/support/ChatTraceAssembler.java`
- `service/impl/AgentChatServiceImpl.java`（协调者，待继续瘦身）

**核心逻辑**
1. `planId != null` → 读 `agent_plan.runtime_mode`，**只按持久化模式**路由；
2. `planId == null` → 意图 + 关键词 + 开关，依次问三个 Entry；
3. 都不命中 → `LEGACY`。

**代码节选**（`ChatRequestRouter.route`）
```java
public Route route(String intentCode, Long planId, String message) {
    // 已有任务：严格按持久化 runtimeMode，禁止关键词抢占
    if (planId != null) {
        AgentPlan plan = agentTaskService.load(planId);
        if (plan == null || !StringUtils.hasText(plan.getRuntimeMode())) {
            return Route.LEGACY;
        }
        return switch (plan.getRuntimeMode()) {
            case AgentTaskService.RUNTIME_MULTI_AGENT -> Route.MULTI_AGENT;
            case ResumeReactEntry.RUNTIME_RESUME     -> Route.RESUME;
            case AgentTaskService.RUNTIME_REACT_SEARCH -> Route.REACT_SEARCH;
            default -> Route.LEGACY;
        };
    }
    // 新任务：意图 + 关键词 + 开关
    if (jobSeekReactEntry.shouldHandle(intentCode, null, message)) return Route.MULTI_AGENT;
    if (resumeReactEntry.shouldHandle(intentCode, null, message)) return Route.RESUME;
    if (jobSearchReactEntry.shouldHandle(intentCode, null))       return Route.REACT_SEARCH;
    return Route.LEGACY;
}
```

**为什么不能"先问关键词"**：`WORKFLOW_RESUME` 的补参消息常含"简历、岗位"，若先走复合入口判断会被抢走，原任务永远续跑不了。

**手写练习 P1**：实现 `route()`，要求
- 有 planId 时**不调用**任何 Entry 的 `shouldHandle`（用 mock 验证零交互）；
- 未知 planId 返回 `LEGACY`；
- 写 4 个用例（RESUME/MULTI/新请求/未知任务）。参考 `ChatRequestRouterTest`。

---

## 3. ReAct 执行内核

**职责**：模型—工具反馈循环的唯一实现。负责轮次、预算闸门、消息配对、协议纠错、终止条件。

**关键类**
- `agent/core/react/ReActRunner.java`（413 行）
- `agent/core/react/TerminalContract.java`（58 行）
- `agent/core/react/AgentRunContext.java`（消息 + 轮次 + 工具计数）
- `agent/core/react/AgentDefinition.java`（提示词、工具白名单、轮次/时限）

**循环骨架**（`runWithTerminal`，节选）
```java
while (true) {
    if (AgentRunExecutionHooks.cancelled()) return failure(TASK_CANCELLED, ...);
    if (deadlineExceeded(definition, context)) return failure(BUDGET_TIMEOUT, ...);
    if (context.turnsExhausted(definition)) return failure(BUDGET_TURNS_EXHAUSTED, ...);

    TaskExecutionBudget budget = AgentRunExecutionHooks.budget();
    if (budget != null && !budget.canContinue()) return failure(...);
    boolean entered = budget != null && budget.tryEnterModelCall();   // 并发闸门
    if (budget != null && !entered) return failure(BUDGET_MODEL_CALLS_EXHAUSTED, ...);

    context.nextTurn();
    AiChatTurnResponse response;
    try {
        TaskBudgetContext.set(budget);                                 // 网关重试共用同一预算
        response = aiModelGatewayService.chatTurn(SCENE_AGENT_REACT, STANDARD,
                AiChatTurnRequest.of(context.messages(), definition.toolSpecs()), userId, traceId);
    } catch (Exception e) {
        return failure(MODEL_ERROR, e.getMessage(), ...);
    } finally {
        TaskBudgetContext.clear();
        if (budget != null) budget.exitModelCall();
    }

    if (deadlineExceeded(definition, context)) return failure(BUDGET_TIMEOUT, "模型返回时已超时", ...);
    if (response.truncated()) { /* 一次纠错机会，仍失败 → PROTOCOL_ERROR */ }
    if (!response.hasToolCalls()) { /* 提示必须用终结工具 */ }
    // 校验 toolCallId → 先写 assistant（含全部 toolCalls）→ 再执行工具 → 写 tool 结果
    // 终结工具命中 → TerminalContract.parseTyped() 校验 → 返回 payload
}
```

**关键设计**
1. **额度只在网关扣**：Runner 只做闸门。否则一次请求会被扣两次（Runner + 网关重试）；
2. **消息配对**：assistant 的每个 `toolCall` 必须有对应 tool 消息，缺一个下一轮直接被模型判非法；
3. **终结必须单独一轮**：同一轮里"又调工具又给结论"不接受；
4. **两层错误**：业务结论（FOUND/EMPTY/NEED_INPUT）与运行时原因码（MODEL_ERROR/BUDGET_*/PROTOCOL_ERROR）分开。

**代码节选**（`TerminalContract`）
```java
public interface TerminalContract<T> {
    String toolName();                       // finish_resume_profile 等
    Class<T> payloadType();
    T parseTyped(Map<String, Object> params);          // 第一层结构校验
    default AgentResult.Outcome outcomeOf(T payload) { return AgentResult.Outcome.SUCCEEDED; }
}
```

**手写练习 P2**：实现一个 80 行的迷你 Runner，支持
- 最大 5 轮、工具调用失败重试 1 次；
- 模型没给工具调用时追加一条 user 提示并继续；
- 命中 `finish` 工具即结束。
**验收**：mock 模型第 1 轮返回工具调用、第 2 轮返回 finish，断言工具被执行一次、结果 payload 正确、轮次=2。

---

## 4. 预算、取消与线程上下文

**职责**：父任务共享一份预算，子节点只继承不重置；取消信号跨线程可见。

**关键类**
- `agent/core/budget/TaskExecutionBudget.java`
- `agent/core/budget/TaskBudgetRegistry.java`（planId → budget）
- `agent/core/budget/TaskBudgetContext.java`（ThreadLocal，给网关用）
- `agent/core/react/AgentRunExecutionHooks.java`（线程钩子：deadline + budget + checkpoint）

**默认值**（`TaskExecutionBudget` 常量）
```java
DEFAULT_DEADLINE_MILLIS = 300_000L; // 5 分钟
DEFAULT_MAX_MODEL_CALLS = 24;       // 模型请求（含重试/备用）
DEFAULT_MAX_TOOL_CALLS  = 30;
DEFAULT_MAX_CONCURRENCY = 2;        // 模型并发
```

**核心逻辑**
1. `restore(modelCallsUsed, toolCallsUsed, usedMillis)`：补参续跑时延续已用额度，**等待用户的时间不计入**；
2. `canContinue()`：超时/取消/额度耗尽 → false；
3. `tryEnterModelCall()/exitModelCall()`：并发闸门（信号量语义）；
4. 并行节点必须显式传递：`AgentRunExecutionHooks.set(deadlineAtMillis, checkpoint, budget)`（另有只传前两个参数的重载），`finally` 清理，否则线程池复用会串任务。

**手写练习 P3**：实现 `TaskExecutionBudget`
- 方法：`restore / canContinue / isCancelled / cancel(reason) / tryEnterModelCall / exitModelCall / remainingMillis`；
- 要求：并发闸门用 `AtomicInteger`，取消用 `volatile`；
- **验收**：20 线程抢 2 个并发位，最多 2 个成功进入；取消后 `canContinue()==false`。

---

## 5. 父任务状态机（防重复执行 / 防取消被覆盖）

**职责**：父计划从 `PLANNED/NEED_CLARIFICATION/FAILED` → `RUNNING` → 终态，全部条件更新。

**关键类**：`agent/application/task/AgentTaskService.java`（213 行）

**代码节选**
```java
@Transactional(propagation = REQUIRES_NEW)
public boolean claimForExecution(Long planId, String expectedStatus) {
    int rows = agentPlanMapper.update(null,
        new UpdateWrapper<AgentPlan>()
            .eq("id", planId)
            .eq("status", expectedStatus)                  // 只有预期状态才能领
            .set("status", AgentPlanStatus.RUNNING.name()) // 领取即 RUNNING
            .set("update_time", new Date()));
    return rows > 0;
}

@Transactional(propagation = REQUIRES_NEW)
public boolean completeExecution(Long planId, AgentPlanStatus status,
                                 String failReason, String pendingQuestion, String finalAnswer) {
    int rows = agentPlanMapper.update(null,
        new UpdateWrapper<AgentPlan>()
            .eq("id", planId)
            .eq("status", AgentPlanStatus.RUNNING.name())  // 仅执行中可落终态
            .set("status", status.name())
            .set("fail_reason", failReason)
            .set("pending_question", pendingQuestion)
            .set("final_answer", finalAnswer));
    return rows > 0;   // false = 已被取消/新执行接管，调用方必须如实返回"状态已变更"
}
```

**为什么 `REQUIRES_NEW`**：领取/写回必须立刻提交，不能被外层长流程（模型调用）拖在同一事务里。

**手写练习 P4**：用一张 `task(id, status, version)` 表实现
- `claim(id, expected)`：`UPDATE ... WHERE id=? AND status=?`，返回影响行数；
- `complete(id, terminal)`：`WHERE status='RUNNING'`；
- **验收**：两线程同时 claim，只有一个成功；取消后 complete 返回 0。

---

## 6. 多 Agent 编排（复合任务）

**职责**：固定骨架 + 合法并行 + 产物复用；模型只负责节点内决策。

**关键类**
- `agent/scenario/jobseek/entry/JobSeekReactEntry.java`（入口 + 状态机）
- `agent/scenario/jobseek/workflow/JobSeekWorkflow.java`（546 行）
- `agent/scenario/jobseek/service/SeekContextResolver.java`（条件/简历）
- `agent/scenario/jobseek/service/SeekArtifactService.java`（产物存取）
- `agent/scenario/jobseek/policy/SeekVersionPolicy.java`（版本规则）
- `agent/scenario/jobseek/assembler/SeekAnswerAssembler.java`（答复）

**骨架**
```
PREPARE_CONTEXT（条件合并 → 简历定位 → 确保已解析）
  → 画像 ∥ 搜索（仅当 关键词与城市都显式给出 才并行）
  → 节点成功即存产物（幂等：同版本不重复写）
  → 搜索为空 → 直接 EMPTY 答复（不调 MatchAgent）
  → MatchAgent → 校验（jobId ⊆ 候选、rank 连续、硬条件）
  → SeekAnswerAssembler（只输出可信字段）
```

**版本规则**（`SeekVersionPolicy`，节选）
```java
public static String resumeVersion(JobResume r) {
    String seed = r.getId() + ":" + (r.getUpdateTime()==null ? "" : r.getUpdateTime().getTime())
                + ":" + hash(r.getRawText());
    return hash(seed);
}
public static String profileVersion(JobResume r, String targetRoleHint) {
    return hash(resumeVersion(r) + "|" + (targetRoleHint == null ? "" : targetRoleHint));
}
public static String constraintVersion(SearchConstraints c, Long resumeId) {
    return hash(resumeId + "|" + c.targetRole() + "|" + c.city() + "|"
              + c.minSalary() + "|" + c.educationReq() + "|" + c.keyword());
}
public static String reportVersion(String cv, Long profileId, Long candidatesId) {
    return hash(cv + "|" + profileId + "|" + candidatesId);
}
```

**幂等保存**（`SeekArtifactService.save`，节选）
```java
AgentArtifact existing = agentArtifactService.findLatest(userId, planId, type, version);
if (existing != null) return existing.getId();          // 同版本不重复写
AgentArtifact saved = agentArtifactService.save(userId, planId, null,
        type, version, toJson(payload), refs);
return saved.getId();
// 失败：抛 IllegalStateException → 上层转 FAILED，禁止声称"已保存"
```

**手写练习 P5**：实现 `JobSeekWorkflow` 骨架（不接模型，节点用 mock）
- 条件：画像/搜索都成功 → 匹配；搜索空 → 直接 EMPTY；
- 画像成功、搜索失败 → **画像产物必须已保存**，返回 FAILED 且带 profileArtifactId；
- 重试同版本 → 复用画像，不再调用画像节点。
**验收**：对应 `JobSeekContextArtifactTest` 两条用例。

---

## 7. 上下文处理（历史 / 预算 / 摘要）

**职责**：把多轮历史压进 token 预算，保证每轮模型输入可控且不丢关键事实。

**关键类**：`agent/support/context/AgentContextBuilder.java`（284 行）、`AgentContextProperties`、`AgentUserContext`

**流程**（`build(userId, traceId, conversation)`，按真实实现）
1. 加载最近窗口：`historyRounds * 2 + 1` 条（多取 1 条用于排除刚保存的当前轮用户消息）；
2. 窗口之外的旧历史 → `loadOverflow` → **增量滚动摘要**（`refreshRollingSummary`，只压缩尚未压缩的部分）；
3. 窗口内消息 `assembleHistory` 拼成历史文本，按条/字符预算截断（记录 `truncated`）；
4. 返回 `BuiltContext(historyText, rollingSummary, truncated, estimatedTokens)`；
5. 长期记忆与用户画像由调用方（聊天链路）在拼 Prompt 时注入，本类只负责"历史 + 摘要 + 预算"。

**关键点**
- **摘要要可增量**：只对"被折叠掉的部分"做摘要，不能每次全量重算；
- **保证配对**：裁剪时不能把 assistant(toolCalls) 与 tool 消息拆开，否则模型直接报错。

**手写练习 P6**：实现 60 行 `ContextBuilder`
- 输入：`List<Message>`、`maxTokens`；
- 规则：保留首条 system、最近 6 条；中间部分拼成一条摘要占位消息；
- **验收**：构造 50 条消息，输出 token < 上限且首尾消息保留、tool 配对完整。

---

## 8. 记忆模块（提取 / 冲突 / 写入 / 注入）

**职责**：把"用户长期偏好与事实"沉淀下来，跨会话复用，且不被一次误提取污染。

**关键类**
- `agent/support/memory/AgentMemoryRuleExtractor.java`（281 行，规则抽取）
- `AgentMemoryLlmExtractor.java`（LLM 兜底）
- `AgentMemoryConflictGate.java`（236 行，冲突裁决）
- `AgentMemoryWritePolicy.java`（87 行，入库闸门）
- `AgentMemoryExpireScheduler.java`（TTL 过期归档）
- 注入侧：`AgentContextBuilder` / `AgentMemoryContextService`

**四道闸**
```
候选提取（规则优先，LLM 兜底）
  → WritePolicy：字段齐全？置信度 ≥ 阈值？敏感信息？疑问句？
  → ConflictGate：同 key 已有值？新值置信度更高？用户是否显式声明？
  → 写入（新值 or 保留旧值 + 标记待确认）
  → 注入上下文（按 TTL 与置信度排序）
```

**代码节选**（`AgentMemoryWritePolicy.allowWrite`）
```java
public boolean allowWrite(AgentMemoryCandidate candidate) {
    if (candidate == null || candidate.getMemoryType() == null
            || !StringUtils.hasText(candidate.getMemoryKey())
            || !StringUtils.hasText(candidate.getMemoryValue())) {
        return false;                                   // 没归属/没 key/没正文
    }
    if (candidate.getConfidence() != null
            && candidate.getConfidence().compareTo(MIN_CONFIDENCE) < 0) {
        return false;                                   // 低置信度不自动沉淀
    }
    String value = candidate.getMemoryValue().trim();
    if (value.length() > MAX_VALUE_LENGTH) return false;
    return !containsSensitiveData(value) && !looksLikeQuestion(value);
}
```
> 敏感判断：手机号/邮箱/身份证正则 + "密码/token/secret" 等词；疑问句判断："什么/吗/？/是不是" —— 避免把"用户问了什么"当成"用户事实"。

**冲突裁决要点**（`AgentMemoryConflictGate.check` → `GateResult` 四态）
| 结果 | 含义 | 处理 |
|---|---|---|
| `PASS` | 无冲突或新值更可信 | 落库（覆盖同 key 旧值） |
| `SKIP_EQUIVALENT` | 同 key 等价值 | 跳过重复写入 |
| `DEFER_TEMPORARY` | 临时性表达（"这次先看杭州"） | 不写入长期事实 |
| `CONFLICT_REVIEW` | 同类型异 key 值重叠 | 留后台裁决，不直接覆盖 |

用户本轮**显式陈述**（"我现在只看杭州"）会提高置信度走 `PASS`；无关闲聊在 `AgentMemoryActionClassifier` 阶段就不产生候选。

**手写练习 P7**：实现 `MemoryService`
- 方法：`extract(message)`（3 条规则：城市、目标岗位、薪资下限）、`allowWrite`、`merge(old, new)`、`inject(userId)`；
- **验收**：先存"城市=上海"，再说"只看杭州" → 注入只含杭州；说"不限城市" → 注入为空；说"你好" → 不产生记忆。

---

## 9. 答案语义缓存

**职责**：重复/近义提问命中历史答案，短路整条链路。

**关键类**：`agent/support/cache/AgentAnswerCacheService.java`（119 行）、`InMemorySemanticCacheStore`、`CacheStore`

**逻辑**
1. `lookup(userId, query)`：embedding → 余弦相似度 → 超过阈值且未过期 → 命中；
2. `remember(userId, query, answer)`：写缓存（带 TTL）；
3. 用户维度隔离：不同 userId 永不共享；
4. 缓存只用于"答案型"请求，带副作用的工具结果不复用。

**手写练习 P8**：实现语义缓存
- `Map<Long, List<Entry>>` + 余弦相似度 + TTL；
- **验收**：同一问题第二次命中；相似度 0.85 的改写命中；不同用户不命中；过期后不命中。

---

## 10. 护栏（Guardrails）

**职责**：在模型输入/输出与工具执行两侧做硬约束。

**关键类**：`agent/support/guardrail/`
- `PromptInjectionGuard`：注入特征（"忽略以上指令"等）
- `PiiMasker`：手机号/邮箱/身份证脱敏（日志、Trace、错误信息）
- `SensitiveOperationGuard` + `ToolAccessGuard`：工具权限与副作用确认
- `ToolOutputJsonValidator`：工具输出 JSON 与关键字段校验
- `HallucinationGuard`：答复中的实体必须来自工具结果

**代码节选**（`PiiMasker` 的能力面）
```java
public Object maskObject(Object value);   // 递归处理 Map/List/String
public String maskText(String text);      // 正则替换：手机号/邮箱/身份证
```

**手写练习 P9**：实现 `PiiMasker.maskText`
- 手机号保留前 3 后 4（`138****1234`）；邮箱保留前 1–3 位与域名；身份证保留前 6 后 3；
- **验收**：给 5 条样例文本，输出与预期完全一致，且不影响普通数字。

---

## 11. 模型网关（路由 / 重试 / 熔断 / 限流 / 扣费）

**职责**：所有模型调用的唯一出口；业务只传"场景 + 消息"。

**关键类**：`service/impl/AiModelGatewayServiceImpl.java`（837 行）

**核心逻辑**
1. `resolveRoute(sceneCode, tier, ...)`：查 `ai_model_route`（ACTIVE）→ 按 `tier` 精确匹配，缺档位则用场景默认 → 灰度/AB 过滤；
2. 取 `ai_model_config`（base_url / api_key / model_identifier，ACTIVE）；
3. 请求前扣额度（**唯一扣费点**，含重试与备用模型）；
4. 失败分类：429/5xx/超时 → 指数退避重试；4xx 参数错 → 不重试，切备用模型；
5. 连续失败 → HALF_OPEN 熔断；
6. 双维限流（场景 + 用户）；
7. Trace 记录耗时/token/费用。

**排障口诀**
- `没有启用的模型路由：X` → 路由表缺 ACTIVE 行；
- `400 + Arrearage` → 账户欠费；
- `401` → key 错；`400 model not found` → `model_identifier` 错；
- 额度不对 → 看是否有人绕过网关直接调模型。

**手写练习 P10**：实现 100 行 `ModelGateway`
- 路由表（Map）+ 重试（最多 2 次，仅 5xx/超时）+ 额度计数（AtomicInteger）+ 熔断（连续 3 次失败后 30s 拒绝）；
- **验收**：mock HTTP 前两次 500、第三次 200 → 返回成功且额度计 3；连续失败后熔断生效。

---

## 12. 工具层与进度事件

**工具注册表**（`agent/tool/registry/AgentToolInvoker.java`）
```java
@PostConstruct
void initHandlers() {
    Map<String, ToolCall> map = new LinkedHashMap<>();
    register(map, "JobSearchTool.searchJobs", (p, m) -> jobSearchTool.searchJobs(...));
    register(map, "ResumeAnalyzeTool.analyzeResume", (p, m) -> resumeAnalyzeTool.analyzeResume(...));
    // ... 8 个业务工具 + 7 个连接器
    this.handlers = Map.copyOf(map);
}
private void register(Map<String, ToolCall> map, String name, ToolCall call) {
    if (map.putIfAbsent(name, call) != null) throw new IllegalStateException("工具名重复：" + name);
}
```
执行顺序固定：**查表 → 执行 → Schema 校验输出 → 包装结果（错误码）**。

**进度事件**（`agent/core/event/AgentProgressEvent`）
```
task_started / node_started / node_completed / waiting_input / task_failed / task_completed
字段：planId, runId, nodeKey, agentType, seq, status, summary, timestamp
```
规则：`node_started` 与 `node_completed|failed` **必须成对**；并行节点由各自完成时发布，不能最后由父流程补发。

**手写练习 P11**：实现工具注册表 + 一个假工具
- 要求：重名注册抛异常；未注册工具返回 `TOOL_NOT_REGISTERED`；
- **验收**：注册 2 个同名 → 启动失败；调用未知工具 → 错误码正确。

---

## 13. 手写练习总表（建议顺序）

| 编号 | 题目 | 对应文件 | 预计 |
|---|---|---|---|
| P1 | 路由（有 planId 严格按模式） | `ChatRequestRouter` | 40 min |
| P2 | 迷你 ReAct 循环 + 终结工具 | `ReActRunner` / `TerminalContract` | 90 min |
| P3 | 共享预算 + 并发闸门 + 取消 | `TaskExecutionBudget` | 60 min |
| P4 | 任务条件领取/写回 | `AgentTaskService` | 40 min |
| P5 | Workflow 骨架 + 产物保存/复用 | `JobSeekWorkflow` / `SeekArtifactService` | 120 min |
| P6 | 上下文裁剪 + 摘要 | `AgentContextBuilder` | 60 min |
| P7 | 记忆提取/冲突/注入 | `memory/*` | 90 min |
| P8 | 语义缓存 | `AgentAnswerCacheService` | 45 min |
| P9 | PII 脱敏 | `PiiMasker` | 30 min |
| P10 | 模型网关重试/熔断/额度 | `AiModelGatewayServiceImpl` | 90 min |
| P11 | 工具注册表 | `AgentToolInvoker` | 40 min |
| P12 | **综合**：把 P1–P11 串成一条"单 Agent 搜索"链路 | 全部 | 半天 |

**P12 验收清单**
- [ ] 输入"找上海 Java 岗位"→ 命中搜索路由
- [ ] 模型第 1 轮调 `job_search`，第 2 轮 `finish_job_search`
- [ ] 候选为空 → 答复"没有符合条件的结果"，不编造
- [ ] 预算耗尽 → 返回 `BUDGET_TIMEOUT`，任务状态 FAILED
- [ ] 取消后迟到结果不覆盖终态
- [ ] 事件序列 `task_started → node_started → node_completed → done`

---

## 14. 一页速答（面试用）

| 模块 | 一句话 |
|---|---|
| 路由 | 已有任务只认 `runtimeMode`，新任务才看意图/关键词/开关 |
| ReAct | 反馈循环 + 消息严格配对 + 终结必须单独一轮；额度只在网关扣 |
| 预算 | 父任务 300s / 24 模型 / 30 工具 / 并发 2，子节点继承不重置 |
| 任务状态 | 领取与写回都是条件更新，取消不会被迟到结果覆盖 |
| Workflow | 固定骨架 + 条件独立才并行 + 节点成功即存产物 + 搜索空不编造 |
| 产物版本 | 画像=简历+方向；候选=条件；报告=条件+上下游产物 ID |
| 上下文 | 保留 system+最近 K 轮+滚动摘要，裁剪不拆 tool 配对 |
| 记忆 | 规则优先、LLM 兜底；四道闸：字段/置信度/敏感/冲突 |
| 缓存 | 用户隔离 + 相似度阈值 + TTL，只缓存答案型请求 |
| 护栏 | 输入注入、PII 脱敏、工具权限与确认、输出 JSON/实体校验 |
| 网关 | 路由→配置→扣费→重试/备用→熔断→限流→Trace |
| 工具 | 注册表唯一登记，重名启动失败；执行顺序：查表→执行→校验→包装 |
