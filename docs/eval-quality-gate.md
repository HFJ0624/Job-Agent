# Eval 质量门禁使用文档（企业级对标 G3）

> 配套脚本：`scripts/eval-quality-gate.sh`
> 目标：把"Eval 防退化"从理念变成机制——核心数据集回归的 passRate **低于绝对下限**或**相对基线下降超阈值**时，门禁失败（退出码 1）。

## 1. 它解决什么问题

Prompt 调优、模型切换、RAG 变更都可能把 Agent 悄悄改坏。没有门禁时，"改坏了"只能靠用户投诉发现。本门禁每次跑核心数据集回归，用数字说话：

- **绝对下限**：`MIN_PASS_RATE`（默认 0.80）——通过率不能低于这条线；
- **相对基线**：`MAX_DROP`（默认 0.05）——比上一次通过的基线下降超过 5% 即失败；
- **基线更新策略**：只在**通过时**更新基线文件，防止把"退化结果"固化成新基线。

## 2. 前置条件

| 项 | 说明 |
|---|---|
| 运行环境 | 能同时访问**后端服务**和**开发库/模型 Key** 的机器（VM 或自建 Runner）。GitHub 托管 Runner 连不上内网，用不了 |
| 依赖 | `curl`、`jq` |
| 数据集 | Admin Eval 页面已维护一个"核心回归"数据集（含工具/RAG/JSON/记忆/Guardrails 各类用例），记下它的 ID |
| 账号 | 后台管理员账号密码（或提前拿好 sa-token） |
| Judge（可选） | 配好 `EVAL_JUDGE` 场景（ai_model_route）后，回答质量类用例会走 LLM 裁判；**没配也能跑**，Judge 自动降级为规则分 |

## 3. 快速开始

```bash
# 最简用法（账号密码登录 → 触发数据集 1 → 轮询 → 门禁）
BASE_URL=http://192.168.10.100:8500 \
ADMIN_ACCOUNT=admin \
ADMIN_PASSWORD='your-password' \
DATASET_ID=1 \
./scripts/eval-quality-gate.sh
```

成功输出示例：

```
[eval-gate ...] 登录成功（admin）
[eval-gate ...] 已触发数据集 1 回归，runId=1024
[eval-gate ...] 批次完成：status=SUCCESS，通过 18/20（passRate=0.9000）
[eval-gate ...] ✅ 质量门禁通过，基线已更新为 0.9000
```

失败输出示例（退出码 1）：

```
[eval-gate ...] ❌ 质量门禁未通过：passRate=0.7500 低于下限 0.80
[eval-gate ...]   运行批次 runId=1024，请到 Admin Eval 页面查看失败详情（含 Judge 理由）
```

## 4. 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `BASE_URL` | `http://localhost:8500` | 后端地址 |
| `ADMIN_ACCOUNT` / `ADMIN_PASSWORD` | - | 登录账号密码；不提供则必须传 `SATOKEN` |
| `SATOKEN` | - | 已登录的 sa-token 值（提供则跳过登录，适合 token 复用场景） |
| `DATASET_ID` | - | **必填**，核心回归数据集 ID |
| `MIN_PASS_RATE` | `0.80` | 通过率绝对下限 |
| `MAX_DROP` | `0.05` | 相对基线最大允许下降 |
| `BASELINE_FILE` | 脚本同级 `.eval-baseline` | 基线文件（存上一次通过的 passRate） |
| `TIMEOUT_SECONDS` | `600` | 批次执行超时（大数据集调大） |
| `POLL_INTERVAL` | `5` | 轮询间隔（秒） |

## 5. 定时执行（VM cron 示例）

每天凌晨跑一次，输出进日志：

```cron
0 4 * * * cd /data/Job-Agent && BASE_URL=http://192.168.10.100:8500 ADMIN_ACCOUNT=admin ADMIN_PASSWORD='***' DATASET_ID=1 ./scripts/eval-quality-gate.sh >> /data/logs/eval-gate.log 2>&1
```

> 密码不要写死在 cron 里：用 `SATOKEN`（定期更新）或从环境变量文件 source。

## 6. 接入 CI 的方式

| 场景 | 做法 |
|---|---|
| 自建 Runner（推荐） | 把本脚本作为 CI 的一个 step，退出码非 0 即 Pipeline 失败 |
| 纯本地/VM | cron 定时跑 + 日志巡检；失败时 runId 直接贴到 Admin Eval 页面定位 |
| GitHub 托管 Runner | **不可用**（连不上内网库与模型 Key）；如确需云端门禁，需先解决网络打通与密钥注入 |

## 7. 与 Eval 平台功能的对应关系

| 门禁能力 | 依赖的平台功能 |
|---|---|
| 触发回归 / 轮询状态 | Eval 异步化（任务 10）：`run-dataset` 投递 Workflow 任务，`GET /runs/{runId}` 查状态 |
| passRate 计算 | 运行批次统计（totalCount/passCount） |
| 失败定位 | Eval 结果页：失败类型（TOOL_SELECT/PARAM/RAG/JUDGE…）+ Judge 理由 + 诊断建议 |
| 回答质量评估 | LLM-as-Judge（G3）：`EVAL_JUDGE` 场景四维评分，失败自动降级规则分 |

## 8. 设计取舍（面试可讲）

1. **基线只在通过时更新**：否则一次事故会把"低分"固化成新基线，门禁失效；
2. **绝对下限 + 相对下降双闸门**：只设绝对下限，质量缓慢下滑发现不了；只设相对基线，首发没有基线时失守；
3. **脚本而非 Java 组件**：门禁是运维行为，不该混进业务进程；bash + HTTP API 复用现有接口，零新增代码路径；
4. **Judge 降级不阻断**：模型不可用时退回规则分，门禁本身永远可用——防御性降级链的最后一环。
