# 无用代码审查清单（静态扫描）

> 扫描范围：`Job-Agent-Bootstrap / Framework / Infra-Ai / Mcp-Tools` 的 main 源码 + `apps/user/src`。
> 判定方式：私有方法/字段/import 在**本文件内**零引用（字段已排除 Lombok 数据类并计入 getter）。
> **这不是自动删除建议**：每条请先按"复核方式"确认，再按批次删除。

## A. 高置信：可直接删（删完编译即可验证）

### A1. 未使用的私有方法

| 文件 | 行 | 方法 | 说明 |
|---|---|---|---|
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 488 | `emitAsStream()` | 已被 `streamWithSummary` 取代（方案 B 流式改造后不再调用） |
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 1629 | `buildPlannedAgentMessage()` | 旧模板链路遗留的 Prompt 拼装，现由 `ChatTraceAssembler` / 执行器内部完成 |
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 1670 | `buildChatTraceInput()` | Trace 组装已迁到 `ChatTraceAssembler`，此方法为残留 |
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 1680 | `buildChatTraceOutput()` | 同上 |
| `com/job/bootstrap/service/impl/JobCommunicationRecordServiceImpl.java` | 946 | `toVO()` | 仅本文件内定义，无调用/方法引用 |
| `com/job/bootstrap/agent/scenario/resume/ResumeReactEntry.java` | 285 | `longOf()` | 简历定位迁到 `ResumeSelectionService` 后残留 |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 466 | `loadCandidatesArtifact()` | 产物加载已直接调用 `SeekArtifactService`，此薄委托无人使用 |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 470 | `loadReportArtifact()` | 同上 |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 494 | `text()` | Workflow 内最后一个调用点已迁走 |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 500 | `toJson()` | 序列化已迁到 `SeekArtifactService` |

### A2. 未使用的私有字段

> 注意：这些字段是 `@RequiredArgsConstructor` 的构造参数，**删除后要同步改测试里的构造**（`AgentChat*Test`、`JobSeek*Test`），否则编译失败。

| 文件 | 行 | 字段 | 说明 |
|---|---|---|---|
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 109 | `NOT_DELETED` | 随会话方法迁到 `ConversationService` |
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 113 | `MEMORY_RETRIEVAL_LIMIT` | 记忆检索已由 `AgentMemoryContextService` 负责 |
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 118 | `aiConversationMapper` | 会话存储已抽到 `ConversationService`，本类不再直接用 |
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 119 | `aiMessageMapper` | 同上 |
| `com/job/bootstrap/service/impl/AgentDailyReportServiceImpl.java` | 72 | `STATUS_NORMAL` | 常量未被引用（同名字面量直接使用） |
| `com/job/bootstrap/service/impl/AgentInboxServiceImpl.java` | 79 | `PRIORITY_LOW` | 常量未被引用（同名字面量直接使用） |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 107 | `DELETED` | 逻辑删除常量未被引用 |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 108 | `DEFAULT_CHAT_PATH` | 路径常量未被引用 |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 116 | `objectMapper` | 本类已无序列化调用（网关内部自建） |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 120 | `aiGatewayProperties` | 配置读取已迁到网关构造函数参数 |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 72 | `jobResumeService` | 准备阶段已抽到 `SeekContextResolver` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 73 | `resumeSelectionService` | 同上 |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 74 | `userJobPreferenceService` | 同上 |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 75 | `agentArtifactService` | 产物读写已抽到 `SeekArtifactService` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 78 | `parameterExtractor` | 同上（条件合并迁走） |

### A3. 未使用的 import（29 处）

<details><summary>展开列表</summary>

| 文件 | 行 | import |
|---|---|---|
| `com/job/bootstrap/service/impl/AgentChatServiceImpl.java` | 34 | `com.job.common.entity.agent.AiMessage` |
| `com/job/bootstrap/service/impl/AgentPlanningServiceImpl.java` | 28 | `java.util.ArrayList` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 5 | `com.fasterxml.jackson.databind.JsonNode` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 30 | `com.job.common.entity.base.ResultCodeEnum` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 41 | `org.springframework.http.HttpEntity` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 42 | `org.springframework.http.HttpHeaders` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 43 | `org.springframework.http.HttpMethod` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 44 | `org.springframework.http.MediaType` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 45 | `org.springframework.http.ResponseEntity` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 46 | `org.springframework.http.client.SimpleClientHttpRequestFactory` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 51 | `org.springframework.web.client.RestTemplate` |
| `com/job/bootstrap/service/impl/AiModelGatewayServiceImpl.java` | 55 | `java.time.Duration` |
| `com/job/bootstrap/service/impl/InterviewQuestionSelectorServiceImpl.java` | 19 | `java.util.Collection` |
| `com/job/bootstrap/service/impl/MockInterviewLearningPlanServiceImpl.java` | 31 | `java.util.Date` |
| `com/job/bootstrap/service/impl/ResumeExportExecutorImpl.java` | 8 | `lombok.RequiredArgsConstructor` |
| `com/job/bootstrap/service/match/HardConditionEvaluator.java` | 9 | `java.math.BigDecimal` |
| `com/job/bootstrap/agent/support/cache/InMemorySemanticCacheStore.java` | 6 | `java.util.ArrayList` |
| `com/job/bootstrap/agent/support/cache/InMemorySemanticCacheStore.java` | 9 | `java.util.List` |
| `com/job/bootstrap/agent/support/compensation/GreetingGenerateCompensation.java` | 15 | `java.util.Date` |
| `com/job/bootstrap/agent/scenario/jobsearch/JobSearchWorkflow.java` | 8 | `com.job.bootstrap.agent.core.react.AgentRunExecutionHooks` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 25 | `com.job.common.entity.agent.AgentArtifact` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 27 | `com.job.common.vo.preference.UserJobPreferenceVO` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 28 | `com.job.exception.BizException` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 36 | `java.math.BigDecimal` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 37 | `java.nio.charset.StandardCharsets` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 38 | `java.security.MessageDigest` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 39 | `java.util.ArrayList` |
| `com/job/bootstrap/agent/scenario/jobseek/workflow/JobSeekWorkflow.java` | 40 | `java.util.LinkedHashMap` |
| `com/job/bootstrap/agent/core/react/TerminalToolContract.java` | 4 | `com.job.bootstrap.ai.turn.AiToolCall` |

</details>

## B. 需人工确认（不要只看引用数就删）

| 类别 | 为什么不能直接删 | 复核方式 |
|---|---|---|
| `*ServiceImpl`（AdminMockInterviewServiceImpl、RagIndexServiceImpl 等） | 通过**接口**注入，类名搜索为 0 不代表没用到 | 搜同名接口（去掉 `Impl`）是否被引用 |
| `workflow/handler/*WorkflowTaskHandler` | 按数据库 `task_type` **字符串**调度，不是 Java 引用 | 查 `agent_workflow_task.task_type` 与 handler 的 `supportType()` |
| `scheduler/*Scheduler` | Spring `@Scheduled` 入口 | 看类上是否有 `@Scheduled` |
| `support/compensation/*Compensation` | 按注册名（工具名）动态查找 | 看 `AgentCompensationRegistry` 的注册 |
| 前端 `src/api/*.ts` 中 8 个导出 | 可能被 `apps/admin` 或动态页面使用 | 在**整个** `Job-Agent-Frontend` 搜函数名 |

前端疑似未使用的导出（`apps/user` 内零引用）：

```
src/api/communication.ts        getCommunicationDetail / createCommunication /
                                saveCommunicationReply / markCommunicationInterview
src/api/hrReplyRecognition.ts   cancelHrReplyRecognition
src/api/reminder.ts             listDueReminders / markReminderRead
src/api/resume.ts               downloadResumeFile
```

## C. 明确不要删

- `*Application`、`*Controller`、`*Mapper`、`*Entity`、`*VO`、`*DTO`、`*Config`、`*Properties`：框架/ORM/序列化入口；
- `AgentPlanExecutorServiceImpl`：旧模板链路仍在服务历史任务；
- `legacy/template/*`：仍有真实调用；
- `support/*` 与 `tool/impl/*`：按名字/注册表动态使用。

## D. 建议删除顺序（每批都可回退）

```bash
# 第 1 批：只删 A3（import），风险最低
# 第 2 批：删 A2（字段）
# 第 3 批：删 A1（方法）
# 第 4 批：处理 B 中确认无用的类/前端 API
mvn -pl Job-Agent-Bootstrap -am clean test   # 每批后执行
cd Job-Agent-Frontend/apps/user && npx vue-tsc --noEmit && npm run build
```

## E. 扫描盲区（如实说明）

- 只做**符号级**引用统计，不解析 Spring 代理、反射、MyBatis XML 的 `resultType`；
- `@Scheduled`、`@EventListener`、`task_type` 字符串调度无法从引用数判断；
- 前端只扫 `apps/user/src`，未扫 `apps/admin`；
- 常量可能被同名字面量"手写"使用，删前建议 grep 一次字面量。
