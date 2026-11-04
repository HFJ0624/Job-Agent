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
