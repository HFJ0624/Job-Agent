# 架构重构报告（2026-11-04）

## 1. 基线

- 起点 SHA：`e4f9c7f`（工作区干净，0 未提交改动）
- 基线构建：`mvn -pl Job-Agent-Bootstrap -am test` = 373 全绿；前端 `npm run build` 通过
- 体量：`AgentChatServiceImpl` 1940 行、`JobSeekWorkflow` 778 行、`AgentPage.vue` 1227 行

## 2. 本轮已完成（代码）

| 项 | 结果 |
|---|---|
| `SeekVersionPolicy` 抽取 | 画像/候选/报告版本规则从 `JobSeekWorkflow` 移出，保存与查找共用同一算法 |
| `SeekAnswerAssembler` 抽取 | 答复排版/公司缺失/薪资元→K 从 Workflow 移出，只接受已校验产物 |
| `SeekArtifactService` 抽取 | 三类产物存取、幂等保存、旧格式兼容集中一处；Workflow 保留"何时保存/复用"决策，内部调用改为薄委托 |
| `SeekContextResolver` 抽取 | 条件三级合并（历史包装/旧 JSON/参数 Map 兼容）、简历定位、未解析简历的准备阶段解析；Workflow 只保留"先合并条件再解析简历"的顺序 |
| `AgentToolInvoker` 注册表化 | 9 分支 switch → 启动时构建的 `Map<String,ToolCall>`；唯一登记入口、重名启动失败、懒初始化兼容单测；Guard/Trace/输出校验顺序不变 |
| `ChatRequestRouter` 抽取 | 新增 `agent/application` 层：复合→简历→搜索→旧链路的优先级集中一处，同步 chat 与 streamChat 共用；Each Entry 的 `shouldHandle` 仍先按持久化 `runtimeMode` 判断、再看开关 |
| 调用点改造 | Workflow 内 5 处版本调用 + 2 处答复调用改为策略/组装类；旧私有方法删除 |
| 验证 | 后端 373 单测全绿（含 `JobSeekWorkflowTest`、`JobSeekContextArtifactTest`） |

## 3. 未完成（如实）

| 计划项 | 状态 | 原因/下一步 |
|---|---|---|
| `SeekAnswerAssembler` | ✅ 已完成 | 用"花括号配平"删除法抽取，Workflow 仅保留编排；373 单测全绿 |

| `ConversationService` 拆分 | 未落地 | `AgentChatServiceImpl` 仍承担会话/护栏/旧链路/Trace；`ChatRequestRouter` 已把路由决策移出 |
| `AgentToolInvoker` 接口化 | 部分完成 | 注册表已落地；核心仍持有 8 个 Tool 引用（参数映射需要），下一步让 Tool 实现统一 handler 接口，内核只依赖注册表 |
| `AgentPage.vue` 拆 composable | 未落地 | 1227 行；计划 `useAgentChat` / `useAgentTask` + `AgentTaskCard`，状态单一来源 |

## 4. 兼容性

- 未改任何 HTTP 路径、SSE 事件名、工具名、持久化字段、状态字符串；
- 未改预算数值、事务边界、身份注入、取消语义；
- 版本算法与原实现逐字符一致（SHA-256 前 8 字节），历史产物可继续命中。

## 5. 回退

- 本轮改动为单文件移动 + 5 处调用替换，`git revert` 对应提交即可；
- 无数据库变更，无配置变更。

## 6. 复验步骤

```bash
cd Job-Agent-backend && mvn -pl Job-Agent-Bootstrap -am test
cd Job-Agent-Frontend/apps/user && npx vue-tsc --noEmit && npm run build
```


## 7. 第 4 批验证补记与更正（2026-11-04）

**更正**：提交 `5f210dd` 的说明里写"容器启动通过"，实际当时启动**失败**，原因是
`target/classes` 残留了迁移前旧包（`com.job.bootstrap.agent.intent.AgentIntentRouter`
与迁移后的 `agent.support.intent.AgentIntentRouter` 同时被扫描），触发
`ConflictingBeanDefinitionException`。源码本身没有重复类。

**执行 `mvn -pl Job-Agent-Bootstrap -am clean package` 后**：

- 启动成功：`Started JobAgentApplication in 14.129 seconds`；
- 工具注册日志：`Agent 工具注册完成，共 15 个`（8 个业务 Tool + 7 个连接器）；
- 无 Bean 重名、无循环依赖、无 Mapper 扫描错误。

**结论（写入验证规范）**：包迁移后必须使用 `clean package`；普通 `package` 会复用
上一次编译的 `target/classes`，把旧包一起打进 jar，产生"源码干净但运行期 Bean 冲突"的假象。


## 8. 收尾轮（2026-11-04，参考 859c681）

### 任务一：已有任务路由 —— 已完成
- `ChatRequestRouter` 增加 `AgentTaskService` 依赖：`planId != null` 时**只按持久化 `runtimeMode`** 选择
  （`WORKFLOW_MULTI_AGENT`/`WORKFLOW_RESUME`/`WORKFLOW_REACT`/其余→LEGACY），不再看消息关键词；
- 新任务（`planId == null`）才走"意图+关键词+开关"判断，Entry 的 `shouldHandle` 传 `null`；
- 同步 `chat()` 与流式 `streamChat()` 共用同一路由结果。
- 测试：`ChatRequestRouterTest` 4 例（RESUME 任务带"简历/岗位"仍走 RESUME 且不询问复合入口、
  MULTI_AGENT 保持、新请求按关键词、未知任务回退 LEGACY）。

### 任务二：拆分 AgentChatServiceImpl —— 部分完成
- ✅ 会话与消息存储抽为 `agent/application/chat/service/ConversationService`
  （getOrCreate / save / touch / 标题生成，含归属校验），`AgentChatServiceImpl` 只调用不实现；
- ✅ 测试改为注入真实 `ConversationService`（复用原有 Mapper mock），行为与断言不变；
- ⏳ 仍未拆出：旧模板链路处理（chatInternal 主流程）、结果与 Trace 组装；
  `AgentChatServiceImpl` 仍是 1700+ 行，后续按"旧链路处理器 / 结果写回器"两块继续。

### 任务三：拆分 AgentPage.vue —— 未完成
页面仍为单文件（1200+ 行）。`useAgentChat`/`useAgentTask`/`AgentTaskCard` 尚未提取。

### 任务五：验证结果（实际执行）
| 项 | 结果 |
|---|---|
| 后端全量单测 | **377 全绿** |
| `mvn -pl Job-Agent-Bootstrap -am clean package` | 成功 |
| Spring 容器启动（`--job.agent.react-search.enabled=true`） | `Started JobAgentApplication in 13.7s` |
| 用户端 `npm run build` | 成功（12.45s，仅有既有 chunk 体积警告） |
| 真实模型链路 | **未验证**：阿里云百炼返回 `Arrearage`（账户欠费），需充值或改用其他 OpenAI 兼容服务 |
| 页面刷新恢复 / 取消 / 补参 / 重试 | **未做浏览器验证**，仅有单测与代码路径覆盖 |

> 启动必须用 `clean package`：包迁移后 `target/classes` 会残留旧包，普通 package 会导致
> `ConflictingBeanDefinitionException`（已在第 7 节记录）。
