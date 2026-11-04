# 目录、包结构与命名规范（现行）

> 与 `PROJECT_MAP.md`、`AGENT_DEVELOPMENT_GUIDE.md` 配套。**以本文件为唯一目录规范**，代码按批次迁移，未迁移部分在文末列出。

## 1. 三种“接口”分开

| 类型 | 位置 | 现有示例 |
|---|---|---|
| HTTP 接口 | `agent/api/controller/front`、`agent/api/controller/admin` | `controller/front/AgentController`（待迁入 agent/api） |
| Java 服务接口 | `<功能>/service` | `service/AgentChatService` |
| 服务实现 | `<功能>/service/impl` | `service/impl/AgentChatServiceImpl` |
| 内核扩展接口 | `agent/core/spi` | 待建（ModelTurnClient / ToolExecutor） |
| 内核接口适配 | `agent/infrastructure/<适配>` | 待建 |
| Mapper | `mapper` | `mapper/AgentPlanMapper` |

规则：service 包只放接口，不放 Impl/DTO/配置/实体；不为每个具体类补接口；Mapper 不手写空的 Impl。

## 2. Agent 目标目录（Bootstrap 根 `com/job/bootstrap/agent/`）

```
agent/
├── api/controller/{front,admin}/      HTTP/SSE 接口（不写 ReAct 循环与 SQL）
├── application/
│   ├── chat/service/{,impl}/          聊天用例接口与实现（协调，不堆所有业务）
│   ├── routing/                       路由表与结果      ← 已落地 ChatRequestRouter
│   └── task/                          父任务领取/写回/取消/快照
├── scenario/
│   ├── resume/{entry,node,contract,model,service,policy}/
│   ├── jobsearch/{entry,workflow,node,contract,model,policy}/
│   ├── jobseek/{entry,workflow,model,policy,assembler,service,config}/
│   └── match/{node,contract,model}/
├── core/
│   ├── react/          通用 ReAct 协议与循环
│   ├── budget/         预算/截止/取消
│   ├── model/          内核输入输出类型
│   ├── spi/            内核最小扩展接口
│   └── event/          进度事件类型（发布接口放 spi）
├── infrastructure/{model,persistence,sse}/   适配实现
├── tool/{spi,impl,registry}/                 工具接口、实现、注册表
├── legacy/template/                          仍在用的旧模板链路
├── support/{guardrail,memory,cache,context,rewrite,eval}/
└── config/                                   Agent 装配与线程池
```

依赖方向：`api → application → scenario → core`；`support` 不反向依赖 scenario；`core` 不 import scenario/tool 实现/Controller/业务 Service/Mapper。

## 3. 数据对象、配置、资源

| 类型 | 位置 |
|---|---|
| Entity | `Framework` 的 `common/entity/<业务>/` |
| 请求 DTO | `Framework` 的 `common/dto/<业务>/` |
| 响应 VO | `Framework` 的 `common/vo/<业务>/` |
| 场景中间模型 | `agent/scenario/<场景>/model/` |
| 内核通用模型 | `agent/core/model/` |
| 配置类 / 属性类 | `config/`、`config/properties/`（*Config / *Properties） |
| MyBatis XML | `resources/mapper/<业务>/`（迁移时同步 namespace） |
| 静态提示词 | `resources/prompts/agent/<场景>/`（数据库管理的提示词保持原来源） |

前端：`pages/` 页面装配、`components/agent/` 展示组件、`composables/agent/` 聊天与任务状态、`api/` 访问封装、`types/` 共享类型、`stores/` 跨页状态。

## 4. 命名

包名全小写；类名后缀固定：`Controller / Service / ServiceImpl / Workflow / Node / Contract / Assembler / Policy / Mapper / Config / Properties`。
扩展接口按能力命名，适配实现可用 `MyBatis*` / `Gateway*` 前缀；Tool 类名与暴露方法名属于注册协议，**不得随包迁移改名**。

## 5. 当前迁移状态（重要）

| 目录 | 状态 |
|---|---|
| `agent/application/routing/` | ✅ 已落地 `ChatRequestRouter` |
| `agent/scenario/jobseek/`（8 类） | ✅ 已迁移：entry（JobSeekReactEntry）、workflow（JobSeekWorkflow）、policy（SeekVersionPolicy）、assembler（SeekAnswerAssembler）、service（SeekArtifactService/SeekContextResolver）、model（JobCandidateArtifactCodec）、config（JobSeekExecutorConfig） |
| `agent/domain/{resume,job,match,tools}` | ⏳ 待迁入 `scenario/*` 与 `tool/impl` |
| `agent/runtime/{react,budget,progress,run,artifact,executor,schema}` | ⏳ 待迁入 `core/*` 与 `infrastructure/*` |
| `agent/orchestration/task/` | ⏳ 待迁入 `agent/application/task/` |
| `agent/support/*` | ✅ 已符合（保留子包） |
| `controller/front/AgentController` | ⏳ 待迁入 `agent/api/controller/front/` |
| `service/impl/AgentChatServiceImpl`（1940 行） | ⏳ 待拆 `application/chat/service/impl` 与 `ConversationService` |

## 6. 迁移批次（每批独立提交、可回退）

1. `orchestration/seek` → `scenario/jobseek/**`（8 文件，同步改 package/import/测试）；
2. `domain/resume|job|match` → `scenario/*`；`domain/tools` → `tool/impl`；
3. `runtime/*` → `core/*` + `infrastructure/*`（`executor` 与 `artifact` 的适配归 infrastructure）；
4. `orchestration/task` → `application/task`；`controller` → `agent/api/controller`；
5. `AgentChatServiceImpl` 拆 `application/chat/service` + `ConversationService`；
6. 前端 `AgentPage.vue` 拆 composable/组件。

每批验证：`mvn -pl Job-Agent-Bootstrap -am test`（当前 373 全绿）→ 必要时 `package` + 启动检查；URL/Schema/工具名/状态字符串/表名/产物版本算法**不得随包迁移改变**。
