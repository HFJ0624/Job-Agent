# Job-Agent

Job-Agent 是一个面向求职场景的智能求职助手系统，目标是把“简历管理、岗位匹配、投递跟进、HR 沟通、面试准备、Agent 对话”串成一条完整闭环。项目采用 Spring Boot 多模块后端 + Vue 3 双前端架构，并基于 LangChain4j 接入大模型、工具调用、对话记忆、调用追踪和 Agent 评测。

## 项目定位

这个项目适合作为 Agent 应用开发方向的作品集项目展示：

- 有真实业务场景：围绕求职者从上传简历到投递、沟通、面试、复盘的完整流程。
- 有 Agent 工程结构：通过 LangChain4j `AiServices` 注册工具，让 Agent 能调用后端业务能力。
- 有可观测能力：记录主对话链路、工具调用链路、耗时、状态和错误信息。
- 有评测雏形：支持配置 Agent Eval Case，并运行用例验证工具调用和答案关键词。
- 有前后台闭环：用户端承载求职流程，管理端承载公司、岗位、导入和 Agent Trace 管理。

## 核心功能

### 用户端

- 用户注册、登录、退出、当前用户信息。
- 个人资料维护、头像上传、地址管理、高德地图地址搜索。
- 岗位列表、岗位详情、职位收藏、沟通入口。
- 求职偏好维护，并根据偏好推荐岗位。
- 简历上传、简历列表、默认简历、简历解析、简历评分、简历文件预览/下载。
- 简历与岗位匹配分析，输出匹配分、优势、风险点和优化建议。
- HR 打招呼语生成，并自动创建沟通记录。
- 投递记录管理、投递状态统计和进度流转。
- 沟通记录管理、HR 回复记录、AI 回复建议、用户回复确认。
- 面试邀约信息抽取、面试信息确认、提醒同步。
- 面试准备材料生成、模拟面试、回答评分、模拟面试复盘。
- Job-Agent 对话助手，支持多轮对话和工具调用。

### 管理端

- 后台登录、退出、当前后台用户信息。
- 用户列表查询。
- 公司管理：分页、详情、新增、修改、删除、Excel 导入。
- 岗位管理：分页、详情、新增、修改、删除、发布、下架、Excel 导入。
- Agent Trace 查询和详情查看。
- Agent Eval 运行接口。
- Prompt 管理、模型管理、社区管理、工作台页面目前以展示雏形为主，后续可继续接入真实接口。

### Agent 能力

- LangChain4j ChatModel 接入 OpenAI 兼容接口。
- `JobAgentAssistant` 作为主 Agent 对话入口。
- `ChatMemoryProvider` 基于会话 ID 保留最近多轮上下文。
- `AgentRuntimeContext` 注入当前用户、会话、Trace、意图信息，避免让模型伪造 userId。
- Agent 工具：
  - `JobSearchTool`：岗位搜索。
  - `JobRecommendTool`：岗位推荐。
  - `JobMatchTool`：简历岗位匹配。
  - `ResumeAnalyzeTool`：简历评分分析。
  - `GreetingGenerateTool`：HR 打招呼语生成。
  - `InterviewPrepareTool`：面试准备。
  - `MockInterviewReviewTool`：模拟面试复盘。
- 专项 AI Service：
  - `HrCommunicationAssistant`：根据 HR 回复生成求职者回复。
  - `InterviewInviteExtractorAssistant`：从 HR 回复中抽取面试邀约结构化信息。
- Trace：
  - 主对话链路记录。
  - 工具调用记录。
  - 成功/失败状态、输入输出、错误信息、耗时。
- Eval：
  - Agent Eval Case。
  - Agent Eval Result。
  - 支持验证期望工具调用和回答关键词。

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
- Apache Tika
- Apache POI
- PDFBox
- LangChain4j
- OpenAI compatible ChatModel
- Lombok
- SpringDoc OpenAPI

### 前端

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Element Plus
- 高德地图 JS API

## 项目结构

```text
Job-Agent
├── Job-Agent-backend
│   ├── Job-Agent-Bootstrap      # Spring Boot 启动模块、Controller、Service、Mapper、配置
│   ├── Job-Agent-Framework      # 通用实体、DTO、VO、枚举、异常和统一返回
│   ├── Job-Agent-Infra-Ai       # LangChain4j、模型配置、AI Service 接口
│   └── Job-Agent-Mcp-Tools      # 预留 MCP/工具模块，目前仍需补充实际实现
├── Job-Agent-Frontend
│   ├── apps
│   │   ├── user                 # 用户端
│   │   └── admin                # 管理端
│   └── package.json
├── README.md
└── LICENSE
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

后端默认读取：

```yaml
spring:
  profiles:
    active: dev,local
```

建议新增本地私有配置文件：

```text
Job-Agent-backend/Job-Agent-Bootstrap/src/main/resources/application-local.yml
```

该文件已在 `.gitignore` 中忽略，适合放本机数据库、Redis、MinIO、模型 Key 等敏感配置。

示例：

```yaml
job:
  ai:
    api-key: ${JOB_AI_API_KEY}
    base-url: https://api.openai.com/v1
    model-name: gpt-4o-mini
    temperature: 0.3
```

同时需要根据你的本机环境配置：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.data.redis.host`
- `job.minio.endpointUrl`
- `job.minio.accessKey`
- `job.minio.secreKey`
- `job.minio.bucketName`

### 启动后端

```bash
cd Job-Agent-backend
mvn clean package
mvn spring-boot:run -pl Job-Agent-Bootstrap
```

默认端口：

```text
http://localhost:8500
```

Swagger/OpenAPI：

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

### 前端环境变量

用户端如需使用高德地图，需要在本地创建：

```text
Job-Agent-Frontend/apps/user/.env.local
```

示例：

```env
VITE_AMAP_KEY=your-amap-js-api-key
VITE_AMAP_SECURITY_JS_CODE=your-amap-security-js-code
```

注意：真实 Key 不应提交到仓库。

## 常用命令

### 后端测试

```bash
cd Job-Agent-backend
mvn test
```

### 用户端构建

```bash
cd Job-Agent-Frontend
npm run build:user
```

### 管理端构建

```bash
cd Job-Agent-Frontend
npm run build:admin
```

### 全量前端构建

```bash
cd Job-Agent-Frontend
npm run build
```

## 当前实现状态

### 已完成度较高

- 用户注册登录与 Sa-Token 登录态。
- 简历上传、解析、评分、文件预览。
- 公司和岗位后台管理。
- 岗位搜索、推荐、匹配。
- HR 打招呼语和沟通记录闭环。
- 投递进度、提醒、面试邀约确认。
- 模拟面试和复盘。
- Agent 对话、工具调用、Trace、Eval 雏形。

### 仍需补齐

- 建表 SQL、数据迁移和种子数据。
- Docker Compose 一键启动 MySQL、Redis、MinIO。
- 管理端真实权限和角色体系。
- Prompt 管理、模型管理页面接真实后端。
- 更完整的单元测试、集成测试和前端测试。
- CI 流水线。
- RAG、Embedding、向量库、检索引用。
- 真正的 MCP Server/Tools 实现。
- Agent 评测数据集、质量指标和可视化报表。

## 后续迭代规划

### 第一阶段：可运行与可展示

- 修复前端类型错误，保证用户端和管理端都能稳定构建。
- 补充 README、启动步骤、环境变量示例、截图和演示数据。
- 增加 `docker-compose.yml`，一键启动 MySQL、Redis、MinIO。
- 增加 Flyway 或 Liquibase，管理数据库表结构。
- 清理敏感配置，提供 `.env.example` 和 `application-example.yml`。

### 第二阶段：Agent 工程化

- 将 Agent 工具调用结果结构化，统一 tool input/output schema。
- 增加 token 统计、模型耗时、工具耗时、失败原因和重试记录。
- 扩展 Agent Eval Case，覆盖工具选择、参数抽取、权限边界、回答质量。
- 在管理端展示评测通过率、失败案例、回归趋势。
- 增加 Prompt 版本管理和 Prompt A/B 评测。

### 第三阶段：RAG 与知识库

- 对简历、JD、公司信息、沟通记录做文本切分。
- 接入 EmbeddingModel。
- 接入向量库，如 pgvector、Milvus 或 Elasticsearch vector。
- 支持检索增强回答，返回引用来源。
- 将岗位匹配从纯规则升级为“规则分 + 语义相似度 + LLM 解释”。

### 第四阶段：MCP 与多工具生态

- 在 `Job-Agent-Mcp-Tools` 模块实现 MCP Server。
- 暴露岗位搜索、简历分析、岗位匹配、提醒创建、投递记录查询等 MCP tools。
- 增加 MCP Inspector 调试说明。
- 支持外部 Agent 客户端调用 Job-Agent 的求职工具能力。

### 第五阶段：多 Agent 工作流

- 简历分析 Agent：负责解析、评分、优化建议。
- 岗位研究 Agent：负责 JD 分析、公司信息整理、风险识别。
- 匹配决策 Agent：负责综合简历、偏好、JD 给出投递建议。
- 沟通 Agent：负责 HR 回复、面试邀约抽取、下一步建议。
- 面试教练 Agent：负责模拟面试、追问、复盘和提升计划。

## 求职展示建议

如果用这个项目面试 Agent 开发岗位，建议重点讲这几条：

1. 业务闭环：不是简单 Chatbot，而是围绕求职流程落地的 Agent 应用。
2. 工具调用：Agent 通过 Tool 调用真实后端服务，而不是只做文本生成。
3. 上下文隔离：userId、conversationId、traceId 由后端上下文注入，不交给模型生成。
4. 可观测：每次主链路和工具链路都有 Trace，便于排查 Agent 失败原因。
5. 可评测：通过 Eval Case 验证工具选择和回答质量，具备持续迭代基础。
6. 后续潜力：RAG、MCP、多 Agent、Guardrails、Human-in-the-loop 都可以自然接入现有架构。

## 许可证

见 [LICENSE](LICENSE)。
