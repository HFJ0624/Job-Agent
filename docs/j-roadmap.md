# Job-Agent 项目升级路线图（J1-J7 跟踪）

> 来源：用户提供的《Job-Agent 项目升级与简历优化清单》（2026-10-23 整理）。
> 本文用途：在仓库内跟踪 J1-J7 的执行状态与完成记录，与 `docs/enterprise-gap-roadmap.md`（G1-G10）分工：
> G 线是企业级能力对标（已全部完成），J 线是求职主业务链路升级。
> 原则不变：渐进式演进、每项带测试与验收、不堆业务按钮。

## 状态总览

| # | 优先级 | 任务 | 状态 |
|---|---|---|---|
| J1 | P0 | 完整多步骤求职任务 | ✅ 代码完成（2026-10-29，267 单测全绿）；用例集扩充待 VM 录入 |
| J2 | P0 | 步骤级保存、失败续跑、执行防重 | ⬜ 待做（增量：执行器租约/批次号、外部结果核对） |
| J3 | P0 | 打通至少一个真实连接器 | ✅ 简历导出真实化（2026-10-29，273 单测全绿）；真库 E2E 待 VM |
| J4 | P1 | 长期记忆更新与遗忘规则 | 🟡 大部分已有（G7/TTL/冲突裁决），剩验证测试补齐 |
| J5 | P1 | 岗位匹配可解释性 | ⬜ 待做（标注样本集 + NDCG@K） |
| J6 | P1 | 统一评测、成本和延迟记录 | 🟡 大部分已有（Trace/Eval/G1），剩任务级预算与 P95 |
| J7 | P2 | 有约束的动态规划 | 🟡 部分已有（LLM 补步/白名单/Replan），剩结构化计划与对比 |

---

## J1｜P0：完整的多步骤求职任务 ✅

**采用方案**：新增“求职主流程”意图与四步模板，复用既有澄清/续跑/占位符传参机制，不新造轮子。

**主流程**（`JOB_SEEK_FLOW` → `AgentPlanTemplateFactory.jobSeekFlowPlan`）：

```
S1 简历分析(ResumeAnalyzeTool) → S2 岗位筛选(搜索/推荐二选一)
   → S3 Top1 匹配分析(JobMatchTool, jobId={{step_2.output.jobs[0].jobId}})
   → S4 投递话术(GreetingGenerateTool, 写操作需确认、G9 可补偿)
```

**关键实现**：
1. **主流程模板**：唯一必填参数 `resumeId`——缺失时计划进入 `NEED_CLARIFICATION`，用户补充后按 planId 续跑（复用既有机制）；keyword/city 可选，从用户目标抽取。
2. **步骤级可执行参数**：`AgentPlanStepTemplate` 新增 `stepParams`（计划级参数为基、步骤级覆盖），模板步骤可声明自己的入参；`buildToolInputSchema` 合并写入 `extractedParams`。
3. **占位符数组下标**：`{{step_N.output.jobs[0].jobId}}`——Executor 的 `STEP_OUTPUT_PLACEHOLDER` 正则与路径解析扩展（`field[idx]` 与裸 `[idx]` 两种形态），越界/类型不匹配按既有语义保留原样（不猜值）。
4. **意图路由**：`JOB_SEEK_FLOW` 规则置顶（完整流程类请求必然含“匹配/简历/岗位”等词，不先拦截会被单目标意图抢走）；LLM 兜底分类器用 `AgentIntentCode.values()` 动态白名单，自动覆盖新意图。
5. **收尾步骤选型说明**：第 4 步用“生成投递话术”而非“面试准备”——面试准备工具依赖真实投递记录（applicationId），首轮流程中不存在，硬凑只会得到澄清阻塞；话术生成是匹配后的自然动作，且带用户确认 + 可补偿（G9），顺带演示完整写工具链路。面试准备是沟通后的下一轮（INTERVIEW_PREPARE 意图已存在）。

**完成记录**（按清单第 5 节模板）：
- 对应任务编号：J1
- 原问题：每个意图只有单目标模板，无法演示“简历→筛选→匹配→行动”的完整任务链；占位符不支持数组下标，取不到岗位列表 Top1。
- 采用方案与理由：新增主流程意图 + 四步模板 + 步骤级参数 + 数组下标占位符；全部复用既有澄清/续跑/传参/确认/补偿机制，增量小、可单测、演示路径完整。
- 代码版本：`wordbuddy-stage` 分支（见 git log “J1” 提交）；模型/Prompt 版本：本次无新场景配置（主流程纯模板 + 确定性工具调用，不依赖新 Prompt）。
- 测试：`AgentPlanTemplateFactoryTest`（4 例：四步结构/占位符声明/旧模板不回退/完成条件齐全）+ `AgentIntentRouterTest`（+2 例：主流程优先于单目标意图）+ `AgentPlanExecutorServiceImplTest`（+2 例：数组下标解析/越界保留原样）；全量 267 绿。
- 剩余限制：Eval 完整任务用例集（30~50 条，含参数缺失/工具失败变体）需在 VM 录入 `agent_eval_case` 后统计成功率；进程退出/模型超时等故障注入属 J2 验收。

## 待办（VM/数据侧，沙箱无法完成）

- [ ] J1：录入 30~50 条完整任务用例（正常流/参数缺失/工具失败三类各占一定比例），统计“完整任务成功率、参数正确率、人工补充次数、失败分布、耗时”五项指标
- [ ] J3：VM 上验证真实导出闭环（配 MinIO → 确认导出 → 文件可下载核对 → 重复导出幂等）
- [ ] J2/J3：真库与外部凭证验证（VM）

---

## J3｜P0：打通至少一个真实连接器 ✅（简历导出）

**选型**：简历导出连接器真实化——本地生成 Markdown + MinIO 上传，零外部凭证、沙箱可完整单测；PDF/DOCX 待文档生成器适配器，不谎称支持。

**完整闭环**（参数校验 → 预览 → 用户确认 → 实际执行 → 结果回查）：
1. **参数校验**：resumeId 必填；格式仅支持 MARKDOWN（空默认 Markdown，PDF/DOCX 明确拒绝并说明原因）；
2. **预览**：未确认时（后台预览按钮 / MCP 调试 / 模型直呼）只返回 PREVIEW + 预估文件名，不执行；
3. **用户确认**：Schema 升级为 `REQUIRED_BEFORE_EXECUTION`，走 chatInternal 既有确认门禁；
4. **实际执行**：确认后由 Executor 调工具 → 归属校验（`getUserResumeRequired`，越权/不存在同一错误信息，不泄露存在性）→ Markdown 拼装（原文优先，忠于原文）→ MinIO 上传（**稳定对象键 `{userId}/{resumeId}.md`，重复导出覆盖同一对象 = 幂等**）→ EXECUTED 响应带 fileKey/fileUrl/fileSize；
5. **结果回查**：`ConnectorToolResponse` 三态（PREVIEW/EXECUTED/ERROR），Trace 明确区分预览与实际执行。

**安全边界**：userId 只信 `AgentRuntimeContext`（模型传参不算数）；确认但 userId 缺失/非法 → 拒绝执行且不调执行器。

**模块边界**：`AgentRuntimeContext` 从 Bootstrap 移至 Framework（`com.job.common.agent`）——连接器工具在 Mcp-Tools 模块，运行时上下文本就是跨模块共享基建；`ResumeExportExecutor` 接口定义在 Mcp-Tools、实现（依赖简历服务/文件存储）在 Bootstrap，端口-适配器模式解耦。

**完成记录**：代码版本见 git log “J3”；测试：`ResumeExportConnectorToolTest` 7 例（预览/执行/参数/格式/归属/身份缺失/默认格式）+ `ResumeExportExecutorImplTest` 6 例（稳定键/忠于原文/parsedJson 回退/归属失败不碰存储/存储失败/非法参数）；全量 273 绿。剩余限制：真实 MinIO E2E 需 VM；PDF/DOCX 需文档生成器适配器（后续项）。
