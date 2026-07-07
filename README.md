# Job-Agent

Job-Agent 是一个面向求职场景的 AI Agent 系统。项目围绕求职者从“简历准备、岗位发现、岗位匹配、投递跟进、HR 沟通、模拟面试、复盘提升、行动确认”这一整条链路展开，目标不是做一个单纯的聊天机器人，而是把大模型能力落到真实业务流程里。

项目采用 Spring Boot 多模块后端 + Vue 3 双前端架构，结合模型网关、Prompt 管理、工具调用、长期记忆、RAG 知识库、Guardrails、Eval 平台、工作流任务队列和可观测性能力，形成一个具备工程化雏形的求职 Agent 应用。

## 项目定位

这个项目适合作为 AI Agent 应用开发方向的完整作品集项目：

- 有真实业务闭环：覆盖简历、岗位、投递、沟通、面试、复盘、学习计划和行动确认。
- 有 Agent 工程结构：不是只把用户输入转给模型，而是有 Planner、Executor、Tool Schema、Guardrails、Memory、RAG 和 Eval。
- 有前后台管理闭环：用户端承载求职流程，Admin 端承载模型、Prompt、RAG、Trace、Eval、工作流和业务数据管理。
- 有可观测和可评测能力：记录模型调用、工具调用、耗时、费用、失败分类、Trace 和回归评测结果。
- 有可继续产品化的基础：工作流任务、外部连接器、邮件通知、行动确认中心等模块为后续自动化求职助理打下基础。

## 核心能力

### 1. 用户端求职流程

用户端面向求职者，主要提供完整的求职工作台能力：

- 用户注册、登录、个人资料、头像、地址和求职偏好维护。
- 首页数据看板，展示岗位、投递、跟进、面试、待处理事项等真实业务数据。
- 简历上传、文件预览、简历解析、简历评分、默认简历设置。
- 岗位列表、岗位详情、岗位收藏、岗位推荐、岗位搜索。
- 简历与岗位匹配分析，输出匹配分、优势、风险点和优化建议。
- 投递记录管理，支持投递状态流转、投递进度跟踪和跟进提醒。
- HR 沟通记录管理，支持 HR 回复识别、回复建议和跟进动作确认。
- Agent Inbox，汇总来自跟进、面试、日报、提醒等模块的待处理事项。
- Agent 行动确认中心，用于承接 AI 生成的可执行建议，避免模型直接越权执行。
- AI 求职助手，支持多轮对话、工具调用、RAG 检索和长期记忆召回。

### 2. AI 模拟面试

AI 模拟面试是项目中相对完整的一条 AI 业务链路：

- 用户选择岗位和简历创建模拟面试。
- 支持从本地题库/RAG 知识库中抽取面试题。
- 支持题库去重、难度配比、知识点关联和用户错题优先。
- 支持摄像头和麦克风交互，用户通过语音回答问题。
- 接入火山引擎/豆包语音 ASR，将语音识别结果保存为文本答案。
- 大模型根据参考答案、RAG 知识和用户回答进行相似度判断、评分和建议生成。
- 面试完成后生成 AI 总体评分、逐题复盘、薄弱知识点、优化建议和学习计划。
- 自动沉淀错题本，支持按知识点筛选、标记掌握，并影响下一轮抽题策略。
- Admin 端可查看用户模拟面试记录、问题、回答、评分和 AI 复盘结果。
- 对常见失败场景做了标准错误码处理，例如 ASR 失败、复盘 JSON 解析失败、总结生成失败等。

### 3. Agent 核心架构

项目中的 Agent 不是自由散漫地让模型调用工具，而是拆成了几层可管理的工程能力：

- Agent Planner：把用户目标拆成计划、步骤、工具选择和完成条件。
- Agent Executor：按照计划执行工具调用，记录步骤状态、输入输出和执行结果。
- Tool Schema：统一工具名称、入参、出参、错误码、权限、副作用和是否需要用户确认。
- Guardrails：处理 Prompt 注入、工具越权、敏感操作拦截、PII 脱敏、JSON 输出校验等问题。
- Long-term Memory：沉淀用户偏好、简历摘要、面试反馈和历史决策，并在对话时按需召回。
- 记忆冲突检测和版本历史：识别用户新旧偏好冲突，保留记忆变更轨迹。
- Model Gateway：统一管理模型配置、API Key、模型路由、超时、重试、熔断和成本统计。
- Prompt 管理：支持 Prompt 模板、版本、场景编码、灰度和可视化维护。
- Agent Trace：记录主链路、工具链路、模型调用、耗时、token、费用和失败原因。
- Agent Eval：通过评测用例验证工具选择、参数准确率、RAG 命中率、JSON 输出和回答质量。

### 4. RAG 知识库

RAG 模块用于把简历、岗位、面试题、知识文档等内容变成可检索知识：

- 支持 RAG 文档入库和 chunk 切片入库。
- 支持 Admin 端查看文档和 chunk 内容，方便排查知识是否正确进入数据库。
- 支持增量索引、删除同步、权限过滤和引用展示的基础能力。
- 支持混合检索和重排序的第一版实现。
- 支持召回质量评测，便于评估知识库是否真的命中用户问题。
- 面试题库也可以通过 RAG 方式参与抽题和答案匹配。

### 5. Admin 管理端

Admin 端承担系统运营和 Agent 工程管理能力：

- 管理员登录和后台工作台。
- 用户、公司、岗位、岗位导入等基础数据管理。
- RAG 文档和 chunk 可视化管理。
- 面试题库管理和模拟面试记录管理。
- 模型配置管理，支持火山引擎、阿里云千问等 OpenAI 兼容模型网关接入。
- Prompt 模板和版本管理，业务方可通过页面维护提示词。
- Agent Plan、Tool Schema、Memory、Trace、Observability 和 Eval 管理。
- 工作流任务管理，查看异步任务状态、重试次数、失败原因和执行进度。
- 外部连接器管理，预留招聘平台、邮箱、日历、通知渠道、简历导出、岗位来源同步等能力。
- 跟进 Agent 管理，支持查看用户求职跟进状态和规则配置。
- 行动项管理，用于查看 AI 生成的待确认动作。

### 6. 工作流与任务队列

项目中引入了异步工作流任务机制，用来承接不适合在请求线程里同步完成的事情：

- 支持任务创建、状态机流转、失败重试、失败恢复和任务进度记录。
- 支持定时调度执行，避免长任务阻塞用户请求。
- 支持邮件通知任务，例如面试准备任务创建后给用户发送邮件提醒。
- 支持 Admin 端查看任务列表、失败原因和处理状态。
- 为后续接入岗位同步、批量 RAG 索引、批量 Eval 回归等长任务提供基础。

### 7. Eval 平台

Eval 平台用于持续验证 Agent 链路质量：

- 支持评测数据集和评测用例管理。
- 支持一键生成核心链路基础用例，覆盖工具、RAG、记忆、Guardrails、JSON 五类场景。
- 支持批量回归运行，记录每次运行结果。
- 支持工具选择准确率、参数准确率、RAG 命中率、回答质量指标等维度。
- 支持失败详情查看，便于定位是模型、Prompt、工具、RAG 还是 Guardrails 的问题。
- 适合项目进入“少加功能，多提质量”阶段后作为质量回归入口。

### 8. 外部连接器

外部连接器模块放在 `Job-Agent-Mcp-Tools` 中，目标是把求职 Agent 的能力向外部系统延展：

- 招聘平台连接器。
- 邮箱连接器。
- 日历连接器。
- 通知渠道连接器。
- 简历导出工具。
- 岗位来源同步工具。

目前该模块以第一版工具化能力和 Admin 管理入口为主，后续可以继续增强真实平台授权、同步任务、失败重试和审计日志。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.5.x
- Spring MVC
- MyBatis-Plus
- MySQL
- Redis
- Sa-Token
- MinIO
- Spring Mail
- Apache Tika
- Apache POI
- PDFBox
- LangChain4j
- OpenAI compatible ChatModel / EmbeddingModel
- SpringDoc OpenAPI
- Lombok

### 前端

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Element Plus
- Element Plus Icons
- 高德地图 JS API

### AI 与工程能力

- 模型网关统一管理
- Prompt 版本管理
- RAG 检索增强
- 长期记忆
- 工具 Schema
- Guardrails
- Trace 可观测性
- Eval 回归评测
- 工作流任务队列
- 火山引擎/豆包语音 ASR

## 项目结构

```text
Job-Agent
├── Job-Agent-backend
│   ├── Job-Agent-Bootstrap      # Spring Boot 启动模块，Controller、Service、Mapper、配置和主要业务实现
│   ├── Job-Agent-Framework      # 通用返回、异常、基础实体、枚举和公共类
│   ├── Job-Agent-Infra-Ai       # AI 基础设施、LangChain4j、模型相关接口
│   ├── Job-Agent-Mcp-Tools      # 外部连接器和 MCP 工具模块
│   └── sql                      # 数据库脚本
├── Job-Agent-Frontend
│   ├── apps
│   │   ├── user                 # 用户端前台
│   │   └── admin                # 管理员后台
│   └── package.json
├── README.md
└── LICENSE
```

## 核心业务流程

### 求职主流程

```text
注册登录
  -> 完善个人资料和求职偏好
  -> 上传并解析简历
  -> 浏览/推荐岗位
  -> 简历岗位匹配分析
  -> 投递岗位
  -> 跟进 HR 沟通
  -> Agent 识别下一步动作
  -> 用户确认执行
```

### AI 模拟面试流程

```text
选择岗位和简历
  -> 创建模拟面试 Session
  -> 从题库/RAG 中抽题
  -> 用户语音回答
  -> ASR 转写
  -> 大模型逐题评分
  -> 生成总体复盘
  -> 沉淀错题本和学习计划
```

### Agent 对话流程

```text
用户输入目标
  -> Guardrails 输入检查
  -> 召回长期记忆和 RAG 知识
  -> Planner 拆解计划
  -> Executor 执行工具
  -> 工具结果进入 Trace
  -> 模型生成结构化回答
  -> Guardrails 输出校验
  -> 可执行动作进入行动确认中心
```

### Eval 回归流程

```text
维护评测用例
  -> 选择数据集
  -> 批量运行回归
  -> 记录工具、RAG、记忆、JSON、Guardrails 结果
  -> 统计通过率和失败原因
  -> 反向优化 Prompt、工具和 RAG
```

## 本地运行

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 16.14+
- MySQL 8+
- Redis
- MinIO

### 后端配置

建议在本地创建私有配置文件：

```text
Job-Agent-backend/Job-Agent-Bootstrap/src/main/resources/application-local.yml
```

该文件适合放置数据库、Redis、MinIO、邮箱、模型 Key、火山引擎 ASR Key 等本地私有配置，不建议提交到仓库。

常见配置项包括：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/job_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your-password
  data:
    redis:
      host: localhost
      port: 6379
  mail:
    host: smtp.example.com
    port: 465
    username: your-email@example.com
    password: your-mail-password

job:
  minio:
    endpointUrl: http://localhost:9000
    accessKey: your-access-key
    secreKey: your-secret-key
    bucketName: job-agent
```

模型和 Prompt 主要通过数据库中的模型配置、模型路由和 Prompt 模板管理。对于火山引擎、阿里云千问等 OpenAI 兼容模型，可以在 Admin 端或数据库中维护 `base_url`、`api_key`、`model_identifier` 等信息。

### 启动后端

```bash
cd Job-Agent-backend
mvn clean package
mvn spring-boot:run -pl Job-Agent-Bootstrap
```

默认服务地址：

```text
http://localhost:8500
```

Swagger/OpenAPI 地址：

```text
http://localhost:8500/swagger-ui/index.html
```

### 启动用户端

```bash
cd Job-Agent-Frontend
npm install
npm run dev:user
```

默认地址：

```text
http://localhost:5173
```

### 启动管理端

```bash
cd Job-Agent-Frontend
npm install
npm run dev:admin
```

默认地址：

```text
http://localhost:5174
```

### 常用命令

后端编译：

```bash
cd Job-Agent-backend
mvn -q -pl Job-Agent-Bootstrap -am -DskipTests compile
```

后端测试：

```bash
cd Job-Agent-backend
mvn test
```

用户端构建：

```bash
cd Job-Agent-Frontend
npm run build:user
```

管理端构建：

```bash
cd Job-Agent-Frontend
npm run build:admin
```

前端全量构建：

```bash
cd Job-Agent-Frontend
npm run build
```

## 数据与配置说明

项目中很多 AI 能力依赖数据库配置，而不是写死在代码里：

- 模型配置：维护模型供应商、Base URL、API Key、模型标识、超时、重试、价格和熔断参数。
- Prompt 模板：按场景编码维护 Prompt 内容、版本、状态和输出格式要求。
- RAG 文档：保存知识文档、chunk 切片、向量索引状态和引用信息。
- Tool Schema：保存工具入参、出参、权限、副作用和确认策略。
- Eval Case：保存评测输入、期望工具、期望参数、期望关键词和评测指标。
- Workflow Task：保存异步任务状态、重试次数、失败原因和执行进度。

因此本地运行前，需要先执行项目已有 SQL 脚本，并根据实际使用的模型供应商补充模型和 Prompt 数据。

## 当前成熟度

这个项目已经具备较完整的功能闭环和 Agent 工程结构，适合用于学习、展示和继续迭代。需要客观看待的是，它仍然是一个工程项目版本，而不是已经经过大规模生产流量验证的商业系统。

已经完成度较高的部分：

- 用户端求职主流程。
- Admin 端业务数据和 Agent 管理入口。
- AI 模拟面试、语音识别、复盘、错题本、学习计划。
- Agent Planner、Executor、Tool Schema、Guardrails、Memory、RAG、Eval。
- 模型和 Prompt 管理。
- Trace、成本、耗时、失败分类等可观测能力。
- 工作流任务队列和邮件通知。
- Agent Inbox、日报、行动确认中心、跟进 Agent 等闭环模块。

后续更值得投入的方向：

- 补齐标准化数据库迁移，例如 Flyway 或 Liquibase。
- 增加核心链路自动化测试和前端端到端测试。
- 补充稳定的演示数据和演示脚本。
- 收敛 UI 视觉规范，提高用户端和 Admin 端一致性。
- 强化权限模型，区分管理员、运营、普通用户等角色。
- 加强生产级安全，例如密钥加密、敏感字段脱敏、审计日志和限流。
- 补充 Docker Compose 或容器化部署文档。
- 持续用 Eval 平台回归 Agent 质量，减少 Prompt 和模型变更带来的不确定性。

## 适合展示的亮点

如果用这个项目展示 AI Agent 工程能力，可以重点讲这几件事：

1. 它不是普通 Chatbot，而是围绕求职业务做了完整闭环。
2. Agent 不是直接“自由调用工具”，而是有计划、执行、工具协议、权限和完成条件。
3. 模型、Prompt、RAG、工具、记忆、评测和 Trace 都有 Admin 管理入口。
4. AI 模拟面试链路包含题库、RAG、语音识别、评分、复盘、错题本和学习计划。
5. 行动确认中心让 AI 给建议，用户做最终确认，降低自动执行风险。
6. Eval 和可观测性让项目具备持续调优能力，而不是靠感觉改 Prompt。

## License

See [LICENSE](LICENSE).
