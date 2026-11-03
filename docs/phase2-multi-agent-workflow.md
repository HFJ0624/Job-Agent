# 第二阶段：三 Agent 协作与 Workflow 编排

> 状态：**代码完成（2026-11-03，本地提交，未推送）**。基于第一阶段验收修复（`880380c`）。
> 验证：Bootstrap **359** 单测全绿（含第一阶段 11 个验收门禁）；**真实模型与真库未联调**（见 §6）。

## 1. 结论先行

用户说“结合我的简历，找上海 Java 岗位，选出最合适的 5 个，并告诉我需要补哪些能力”时：

```
JobSeekReactEntry（父计划 WORKFLOW_MULTI_AGENT）
  → PREPARE_CONTEXT（简历归属/解析/条件三级合并）
  → ResumeAgent ∥ JobSearchAgent（条件独立时并行；否则先画像后搜索）
  → JOIN_RESULTS（搜索为空直接返回，不编造匹配）
  → MatchAgent（只读预览 + 证据化比较）
  → VALIDATE_REPORT（候选/硬条件/排名代码校验）
  → RESPOND（可信字段重建的答复）
```

三个 Agent 都走**同一个 ReAct 内核**（`ReActRunner.runWithTerminal`）与**同一套终结界契约**（`TerminalContract<T>`），
不是三次 Prompt 文本生成。Agent 之间只通过**结构化产物**传递事实。

## 2. 架构分工

| Workflow 控制 | Agent 决定 |
|---|---|
| 身份/归属、简历解析（准备阶段写入）、条件快照与版本 | 读哪些段落、怎么组织画像 |
| 节点依赖与合法并行、预算（父任务总预算/子节点收紧） | 搜什么关键词、翻哪一页 |
| 状态迁移、原子领取、检查点、暂停续跑 | 比较哪些候选、如何解释差距 |
| 产物落库与版本校验、硬条件复核、最终答复 | 提出结构化结论（只提交提议） |

**并行条件**：用户明确给出目标关键词 + 城市（搜索不依赖画像）时才并行；否则串行（画像 → 用画像方向作召回词 → 搜索）。
并行使用 Spring 管理的**有界**线程池（core=2/max=2/queue=8，Abort 策略降级串行），不使用公共池。

## 3. 三个 Agent

### ResumeAgent（`agent/resume`）
- 工具：`read_resume`（摘要+分段索引）、`read_resume_section`（按合法 sectionId 读原文）；
- 终结：`finish_resume_profile`；
- 输出 `ResumeProfile`：resumeId/resumeVersion、skills、experiences、targetRole(+inferred)、**evidence（fact + quote）**、missingInformation、uncertainties；
- **防编造机制**：每条 `evidence.quote` 必须能在简历原文（归一化空白）中找到，否则整单失败（`EVIDENCE_NOT_IN_RESUME`）；
- 简历定位：显式 resumeId > 名称匹配 > 默认简历 > 唯一简历；多份无法确定 → `NEED_INPUT`（列出名称，不要求写 ID）；原始文件未解析在 PREPARE_CONTEXT 触发解析（写操作不伪装成只读）。

### JobSearchAgent（复用第一阶段）
- 直接调用 `JobSearchWorkflow` 节点（**不经过** `JobSearchReactEntry`，不重复创建会话/父任务/聊天消息）；
- 返回 `JobCandidates` 结构化产物（jobId 去重、来源、检索时间、分页未知置空）；
- 沿用第一阶段的硬条件锁定与结果验真（候选必须来自真实工具结果）。

### MatchAgent（`agent/match`）
- 工具：`read_job_detail`、`preview_job_match`、`read_resume_evidence`；
- **复用** `JobMatchServiceImpl`：抽出 `previewMatch`（同一套算法、**不写匹配记录**）；原 `matchJob` 行为不变；
- 终结：`finish_match_report`；
- 输出 `MatchReport`：rankedItems（jobId/rank/ruleScore/matchPoints/gaps/suggestions/evidenceRefs）、uncertainties、completedCount/totalCandidates；
- 代码校验：jobId ⊆ 当前候选、不重复、rank 连续、数量 ≤ Top-K、硬条件不合格不得进入；**ruleScore 以服务端预览为准**（模型自报分数不采信）；缺口只表达“简历未见证据”。

## 4. 父任务 / 子 run / 产物

**SQL**：`sql/08_migration_phase2_multi_agent.sql`
- `agent_artifact`（userId/planId/runId/type/schemaVersion/inputVersion/contentJson/sourceRefs）；
- `agent_run` 增加 `node_key`、`agent_type`、`input_version`（一个父计划下多个子 run 的定位：planId + nodeKey + inputVersion）；
- `agent_plan.runtime_mode`（sql/07 已有列，实体已补），复合任务固定 `WORKFLOW_MULTI_AGENT`。

**版本与复用**：
- 画像版本 = hash(简历ID + 更新时间 + 原文)；候选版本 = hash(简历ID + 条件快照)；报告继承条件版本；
- 同版本产物存在则**跳过对应节点**：改城市 → 画像复用、搜索+匹配重跑；换简历 → 画像与报告失效。

**恢复**：`NEED_INPUT` 时父计划置 `NEED_CLARIFICATION`，下一轮带 `planId` 路由回本引擎并继续；子节点失败保留已完成产物（重试只重跑失败节点）。

## 5. 统一 chat / SSE

`chat` 与 `streamChat` 使用**同一套分发**（`rateIntent → 复合 → 独立搜索 → 旧模板链路`）：
- 复合请求 → `JobSeekReactEntry`；只找岗位 → 第一阶段 `JobSearchReactEntry`；其余 → 旧链路（事务语义不变）；
- 新链路当前把最终答复作为**单个 token** 推送（token/done/error 协议不变，前端无需改造）；
- 新分支均为「非事务分发 + 会话/消息短事务准备 + 事务外执行」。

## 6. 已验证 / 未执行（如实记录）

**已验证（mock 模型 + Mockito，非端到端）**：
- 正常复合链路：画像（含证据回溯）→ 搜索（结果验真）→ 匹配（只读预览+校验）→ 答复含真实岗位/匹配点/缺口，无“已投递”等未执行动作；三类产物落库（`JobSeekWorkflowTest`）；
- 缺少简历：返回 `NEED_INPUT`（列名称/提示上传），**不调用模型**、不编造匹配；
- 条件变化（上海→杭州）：画像产物复用（模型调用只剩搜索+匹配 4 轮），候选/报告按新版本重跑。
- 回归：第一阶段 11 个验收门禁 + 全量 359 绿。

**未执行 / 未实现**：
1. **真实模型与真库端到端未执行**（需 `AGENT_REACT` 场景 + MySQL/MinIO；执行 `sql/07`、`sql/08`）；
2. **SSE 进度事件未接**：未发送 `node_started/node_completed/waiting_input`（当前仅复用 token/done/error），前端进度展示未做；
3. **“只分析简历”未接 ResumeAgent**：单简历分析仍走旧模板链路，本阶段只把复合任务做实；
4. 并行分支（同时给出关键词+城市）已实现但未写专项测试；
5. `agent_plan.runtime_mode` 只在复合入口写入；第一阶段独立搜索仍不建 plan。

## 7. 启用与运行

```yaml
job:
  agent:
    react-search:
      enabled: true   # 复合任务与独立搜索共用此开关（关闭时旧链路完全不变）
```

```bash
# 1) 执行迁移（新增产物表 + run 节点字段）
#    sql/08_migration_phase2_multi_agent.sql
# 2) 配置模型路由场景 AGENT_REACT（复用现有 ai_model_route，模型需支持 tools）
# 3) 全量回归
mvn -pl Job-Agent-Bootstrap -am test
```

**演示任务（预期过程）**：发“结合我的简历，找上海 Java 岗位，选出最合适的 5 个，并告诉我需要补哪些能力”
→ 父计划 `WORKFLOW_MULTI_AGENT` 创建 → ResumeAgent 调 `read_resume`/`read_resume_section` → `finish_resume_profile`（含原文证据）
→ JobSearchAgent 调 `JobSearchTool.searchJobs` → `finish_job_search`（候选真实）
→ MatchAgent 调 `preview_job_match`（服务端规则分）→ `finish_match_report`
→ 答复：真实岗位（公司/城市/薪资）+ 匹配点/缺口 + 建议 + 实际数量。

## 8. 剩余工作

1. 真实环境联调（模型 tools 兼容性、真库 CAS/检查点、产物归属）；
2. SSE 节点进度事件 + 前端任务标识保留（`planId` 已在 `AgentChatVO` 返回）；
3. 只分析简历路由到 ResumeAgent（复用本阶段节点）；
4. 并行分支专项测试与预算共享细化（子节点当前各自收紧，父预算上限统一扣除未实现）。
