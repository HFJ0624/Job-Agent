# 阶段 0 测试基座 · 测试报告

> 对应路线图任务 3：测试基座修复与主链路测试补强。
> 目标：让 `mvn test` 可信、为主链路核心模块补单测、建立覆盖率基线。
> 本阶段**不改动任何生产代码**，仅新增/修复测试与测试基础设施。

---

## 1. 已完成的工作

| 工作项 | 说明 | 提交 |
|---|---|---|
| Executor 幂等测试 | `AgentPlanExecutorServiceImplTest`（8 用例）：计划级/步骤级幂等、RUNNING 并发拦截、残留 reclaim、抢占竞争、失败终止 | `95d5473` |
| 测试清理 | 删除无断言的 `Eat.java`；`ExternalConnectorIntegrationTest` 去除对 hutool 的隐式依赖，改用 Spring Test 自带的 Jackson 解析 preview 返回 JSON | `2ded362` |
| 护栏单测（Batch 1） | `SensitiveOperationGuardTest`(6) + `ToolAccessGuardTest`(7) + `HallucinationGuardTest`(5) | `2ded362` |
| 工具分发 / 意图路由单测（Batch 1） | `AgentToolInvokerTest`(4) + `AgentIntentRouterTest`(8) | `2ded362` |
| JaCoCo 接入 | `Job-Agent-Bootstrap/pom.xml` 加入 `jacoco-maven-plugin`（`prepare-agent` + `report`，**不设门禁，只出数字**） | 本提交 |

**本次运行结果：38 个用例全部通过（0 失败 / 0 错误）。**

---

## 2. 测试结果汇总

| 测试类 | 用例数 | 覆盖对象 |
|---|---|---|
| `AgentPlanExecutorServiceImplTest` | 8 | 计划级幂等、步骤级幂等、RUNNING 并发拦截、残留 reclaim、抢占竞争失败、失败终止后续步骤、无工具步骤跳过 |
| `AgentToolInvokerTest` | 4 | switch 工具分发、未知工具→`TOOL_NOT_REGISTERED`、输出校验失败→failed |
| `SensitiveOperationGuardTest` | 6 | 只读/`WRITE_BUSINESS_RECORD` 直接放行、高风险副作用未确认拦截、已确认放行、显式 `requiresUserConfirmation` 拦截 |
| `ToolAccessGuardTest` | 7 | 上下文缺失/越权/空允许列表拦截、正常放行、trim 匹配 |
| `HallucinationGuardTest` | 5 | 失败声称成功替换、无依据声称加提示、正常脱敏透传、PII 脱敏、空文本 |
| `AgentIntentRouterTest` | 8 | 7 类意图关键词路由、复盘先于面试的顺序回归、未知/空输入回落 `GENERAL_CHAT` |
| **合计** | **38** | — |

---

## 3. 覆盖率基线（JaCoCo）

> **口径说明**：本次基线基于"已写的 Agent 测试"做 scoped 运行，反映**被测试代码的真实覆盖**。
> 因此模块整体数字偏低是预期起点——绝大多数类（Controller、Service、RAG、工具类）尚未有测试。
> 报告路径：`Job-Agent-Bootstrap/target/site/jacoco/index.html`（含 `jacoco.csv` / `jacoco.xml`）。

### 3.1 被测试的核心类（重点）

| 类 | 行覆盖 | 说明 |
|---|---|---|
| `AgentIntentRouter` | **100%** | 7 类意图 + 顺序回归全命中 |
| `AgentIntentCode` | 100% | 枚举常量 |
| `SensitiveOperationGuard` | **100%** | 确认拦截全分支覆盖 |
| `ToolAccessGuard` | **100%** | 越权/上下文缺失拦截全分支覆盖 |
| `HallucinationGuard` | 87.9% | 失败替换 / 依据不足提示两条主路径均覆盖 |
| `AgentPlanExecutorServiceImpl` | 85.3% | 幂等 / 恢复 / 抢占主路径覆盖 |
| `AgentRuntimeContext` | 69.4% | ThreadLocal set/clear/确认态 |
| `AgentToolInvoker` | 41.3%（分支 20.5%） | switch 共 11 个分支，本基线仅覆盖 3 条分发路径 + 未知 + 校验失败 |
| `PiiMasker` | 8.3% | 仅覆盖 `maskText` 单条手机号正则路径，其余脱敏规则未触达 |

### 3.2 包级覆盖（agent 相关）

| 包 | 行覆盖 | 分支覆盖 |
|---|---|---|
| `agent.intent` | 100% | 100% |
| `agent.context` | 64.1% | 50.0% |
| `agent.executor` | 41.3% | 20.5% |
| `agent.guardrail` | 35.0% | 32.0% |
| `agent.plan` | 0.0% | 0.0% |
| `agent.memory` | 0.0% | 0.0% |
| `agent.tools` / `agent.schema` | 0.0% | 0.0% |
| `service.impl`（整体） | 2.5% | 1.6% |

> 注：`agent.guardrail` 包均值为 35%，是被 `PiiMasker`（8.3%）拉低；三个 Guard 类本身均为 87.9%~100%。
> `service.impl` 整体 2.5% 是因为除 `AgentPlanExecutorServiceImpl`（85.3%）外，其余 Service 实现均无测试。

### 3.3 模块整体

- **OVERALL 模块行覆盖：3.0%（456 / 15114 行）**
- 这一数字**不代表代码质量差**，而是"测试刚起步"的真实写照：目前只有 Agent 执行/护栏/意图这一条主链路有测试，其余约 14.6k 行尚未纳入测试网。这正是阶段 0 要建立的"起点基线"。

---

## 4. 已知缺口（诚实记录）

1. **意图路由为连续子串匹配**：`AgentIntentRouter` 用 `String.contains` 做关键词匹配，自然语序如"帮我分析**一下**简历""帮我**找 Java 后端**岗位"因关键词不连续而回落 `GENERAL_CHAT`。属路由器已知局限，留待路线图 **Task 5（规则 + LLM 兜底分类）** 处理。
2. **`agent.plan` / `agent.memory` / `agent.tools` / `agent.schema` 包 0%**：模板工厂、参数抽取器、工具类、Schema 注册表尚未有单测。
3. **无 `@SpringBootTest` 集成测试**：用户决定不在本阶段做 Spring 集成冒烟（需定 Docker/Testcontainers 或 H2）。
4. **`PiiMasker` 覆盖不足**：仅触达手机号一条正则，邮箱/身份证/token 等脱敏规则未测试。

---

## 5. 下一步建议（呼应路线图）

| 优先级 | 方向 | 对应任务 |
|---|---|---|
| 中 | 补 `agent.plan`（模板工厂/参数抽取器）单测，把 `agent.plan` 从 0% 拉起 | 任务 3 延伸 |
| 中 | 在 CI 加 `mvn test` 钩子，固化"测试全绿"叙事 | 任务 3 |
| 中 | 集成测试：用 Testcontainers-MySQL 或 H2 做装配冒烟（mock 模型网关，不调真实模型） | 任务 3 |
| 高 | RAG 接入主链路后，补 `RagRetrievalServiceTest` + 降级 Trace 测试 | 任务 2 |
| 高 | 引入覆盖率门禁（如 agent 包 ≥ 40%）作为回归护栏 | 任务 10 |

---

## 6. 如何复现

```bash
# 运行已写的 Agent 测试并生成 JaCoCo 报告
mvn -pl Job-Agent-Bootstrap -am test \
  org.jacoco:jacoco-maven-plugin:0.8.12:report \
  -Dtest='AgentPlanExecutorServiceImplTest,AgentToolInvokerTest,SensitiveOperationGuardTest,ToolAccessGuardTest,HallucinationGuardTest,AgentIntentRouterTest' \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false

# 查看报告
open Job-Agent-Bootstrap/target/site/jacoco/index.html
```

*报告生成于 2026-10-13，基于 `wordbuddy-stage` 分支。阶段 0 完成后停止，不自动进入 Executor 深化。*
