# 第一阶段：ReAct + Workflow（JobSearchAgent）

> 状态：**代码完成（2026-11-03），三提交**：协议层 `37d2b8f`、ReAct 内核 `5463fd2`、Workflow/状态/入口（本提交）。
> 本阶段只把“岗位搜索”一条链路做实；ResumeAgent / MatchAgent 只留接口形态（同一套 `AgentDefinition` + `ReActRunner`），未实现。

## 1. 架构边界

**Workflow 控关键节点，ReAct 控节点内决策。**

| 由 Workflow 控制（不允许模型绕过） | 由 ReAct 决定（模型自主） |
|---|---|
| 身份（userId 只来自后端运行时上下文） | 选择 `job_search` / `job_recommend` |
| 条件快照与硬条件锁定（城市/薪资/学历） | 调整等价关键词、页码、页大小 |
| 工具白名单会话（白名单 ∩ 注册 ∩ 权限） | 根据 Observation 决定继续查还是终结 |
| 状态迁移、原子领取、预算与超时 | 输出 FOUND/EMPTY/NEED_INPUT/FAILED 提议 |
| 结果验真（候选必须来自真实工具结果）、候选重建、对外响应 | |

**固定骨架**：`VALIDATE_INPUT → JOB_SEARCH_REACT → VALIDATE_RESULT → RESPOND`

## 2. 文件清单

**提交 A：结构化模型协议**
- `ai/turn/AiChatMessage|AiToolCall|AiToolSpec|AiChatTurnRequest|AiChatTurnResponse`
- `ai/AiModelHttpClient` + `RestTemplateAiModelHttpClient#chatTurn`（请求体/响应解析）
- `service/AiModelGatewayService#chatTurn` + `AiModelGatewayServiceImpl`（复用路由/限流/熔断/重试/日志；接口默认抛错，不静默退化）

**提交 B：ReAct 内核**
- `agent/react/AgentDefinition`（白名单+预算 6 轮/8 次/180s）
- `agent/react/AgentRunContext`（每 run 独立消息列表与预算计数）
- `agent/react/AgentResult`（业务结论与运行时原因码分离）
- `agent/react/TerminalToolContract`（`finish_job_search` 第一层校验）
- `agent/react/ReActRunner`（反馈循环、配对、单轮终结、修正、预算截断、重复查询熔断）

**提交 C：Workflow + 状态 + 入口**
- `agent/search/JobCandidates` + `JobCandidatesAdapter`（搜索对象/推荐数组统一、去重、分页未知置空）
- `agent/search/SearchConstraints`（显式>偏好、来源标记、结果侧硬条件复核）
- `agent/search/JobSearchWorkflow`（四节点 + 结果验真 + 候选重建 + 确定性答复）
- `agent/search/JobSearchReactEntry`（`NOT_SUPPORTED` 挂起外层事务、run 生命周期、续跑）
- `agent/search/ReactSearchProperties`（`job.agent.react-search.enabled`，默认 false）
- `agent/run/AgentRunService`（短事务创建/原子领取/持有权写回）+ `mapper/AgentRunMapper` + `entity/agent/AgentRun` + `enums/AgentRunStatus`
- `sql/07_migration_phase1_agent_run.sql`（agent_run 表 + agent_plan.runtime_mode）
- `tools/JobSearchTool`（受限分页重载，旧入口不变）

## 3. 状态与结果协议

**run 状态**：`PENDING → RUNNING → SUCCEEDED / FAILED`；补参时 `RUNNING → WAITING_INPUT → RUNNING`。

**消息序列**（`messages_json`）：完整保存，含 `assistant.tool_calls` 与 `tool` 结果（按 `toolCallId` 配对）；大于 1000 字符也完整保存，恢复不使用摘要。

**终结结果**（`result_json`）：
```json
{"outcome":"FOUND|EMPTY|NEED_INPUT|FAILED","reasonCode":null,
 "candidateIds":[1,2],"missingFields":[],"toolCallsUsed":2,"turnsUsed":3}
```

**运行时原因码**（模型不能伪造）：`BUDGET_TURNS_EXHAUSTED`、`BUDGET_TOOL_CALLS_EXHAUSTED`、`BUDGET_TIMEOUT`、`PROTOCOL_ERROR`、`MODEL_ERROR`；Workflow 侧补充：`PROPOSAL_VERIFICATION_FAILED`、`EMPTY_WITHOUT_SEARCH`。

**验证规则**（Workflow 复核，模型两侧都骗不过）：
- FOUND：`candidateIds ⊆ 真实工具结果`，且逐条通过硬条件复核（城市/薪资/学历；无法验证视为不合格）；
- EMPTY：必须“至少一次合法查询成功且无合格候选”，否则 `EMPTY_WITHOUT_SEARCH`；
- NEED_INPUT：必须有问题与缺失字段；
- 对外答复由服务端用真实字段重建（岗位名/公司/城市/薪资），不采用模型转述。

## 4. 启用方法

```yaml
job:
  agent:
    react-search:
      enabled: true   # 默认 false；关闭走原模板链路
```
另需配置模型路由场景 `AGENT_REACT`（复用现有 `ai_model_route`，模型需支持 OpenAI 兼容 tools 协议）。

执行 SQL：`sql/07_migration_phase1_agent_run.sql`。

## 5. 四种路径示例

| 路径 | 触发条件 | 对外答复 | run 状态 |
|---|---|---|---|
| FOUND | 真实查询有候选 + 终结提议候选全部通过复核 | 服务端重建的岗位列表 | SUCCEEDED |
| EMPTY | 至少一次查询成功、无合格候选 | “按条件查询但没有找到…可以放宽条件再查” | SUCCEEDED |
| NEED_INPUT | 模型提议缺参（如城市不明） | 模型给出的具体问题 | WAITING_INPUT（补参后同 run 续跑） |
| FAILED | 预算耗尽/模型异常/协议错误/候选伪造 | 真实失败原因（不伪装成没岗位） | FAILED |

## 6. 验证命令与结果

```bash
# 全量单测（含本阶段新增 39 例）
mvn -pl Job-Agent-Bootstrap -am test
```

**实际结果**（2026-11-03 执行）：`Tests run: 342, Failures: 0, Errors: 0`（Bootstrap）。

新增测试覆盖：
- 协议 5 例：消息序列序列化 / content=null+tool_calls 合法 / 空响应失败 / 截断标记 / 无工具名丢弃
- Runner 7 例：反馈循环 / 多调用配对 / 非法终结修正 / 越权工具 / 工具预算耗尽 / 重复查询熔断 / 终结混轮
- Workflow 7 例：候选重建 / 伪造候选拒绝 / 硬条件不符拒绝 / 未查询声明 EMPTY 拒绝 / 查询后 EMPTY / 城市篡改覆盖 / NEED_INPUT 透传
- 适配层 5 例 + 约束 4 例 + run 状态 5 例（领取冲突/旧 attempt 拒绝/长消息完整保存）+ 入口 6 例（开关/任务路由/重复请求/状态映射/续跑复用消息）

## 7. 未完成与限制（如实记录）

1. **未做真实模型/真库联调**：本阶段全部验证为 mock 模型 + Mockito 单测；真实 `AGENT_REACT` 场景联调需在具备 API Key 与数据库的环境执行（步骤见 §4）。
2. `agent_plan.runtime_mode` 字段已建但尚未写入：当前入口在“无计划”状态下创建 run，未把 `runtimeMode` 固化到计划；后续接 ResumeAgent/MatchAgent 时补。
3. `JobRecommendTool` 不返回总数/分页，适配层按“未知置空”处理，不做推断。
4. 发邮件/导出/日历等写工具未纳入 JobSearchAgent 白名单（符合文档要求）。
