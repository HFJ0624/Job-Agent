# Eval 平台重做 · 核心练习手册

> 目标：把「评测平台」从"一个杂乱页面"改成**普通人也能看懂**的流程：
> 选题库 → 跑一次评测 → 看报告 → 和基线对比。
>
> 仓库里我已经完成：数据/接口/页面/样式。**下面 3 个核心逻辑留给你写**，
> 每段都有完整参考代码、测试用例和验收标准。写完把 `SimpleEvalFailureClassifier`
> 里的 `TODO(练习)` 替换掉即可。

---

## 0. 你已经拿到的部分（不用写）

| 层 | 内容 |
|---|---|
| 后端接口 | `GET /admin/agent/eval/overview`（概览：指标/趋势/失败 Top）<br>`GET /admin/agent/eval/batches/{runId}/report`（批次报告） |
| 后端实现 | `EvalBatchReportServiceImpl`：取 run → 取逐用例结果 → 按分类聚合 |
| VO | `EvalOverviewVO`、`EvalBatchReportVO`、`EvalFailureCategoryVO`、`EvalTrendPointVO` |
| 前端 | `views/agent/eval/EvalPlatformView.vue`：概览/题目库/历史批次 + 报告抽屉，全中文人话文案 |
| 数据 | 直接复用 `agent_eval_run`（本身就是一次全量运行的聚合记录），不新增表 |

---

## 练习 1：失败归因（最重要）

**文件**：`Job-Agent-Bootstrap/src/main/java/com/job/bootstrap/application/agent/service/quality/SimpleEvalFailureClassifier.java`

**目标**：把一条失败结果归到**唯一**分类，报告才能聚合、筛选、对比。

### 优先级（从高到低，先命中先返回）

| 顺序 | 条件 | 分类 |
|---|---|---|
| 1 | `passStatus == 1` | `NONE`（通过） |
| 2 | `expectedToolName` 非空且 `toolSelectPass == 0` | `TOOL_MISMATCH` |
| 3 | `toolSelectPass == 1` 且 `toolParamPass == 0` | `PARAM_MISS` |
| 4 | `ragHitPass == 0` 且用例期望了 RAG（`expectedRagDocumentId != null`） | `RETRIEVAL_EMPTY` |
| 5 | `answerKeywordPass == 0` 或 `judgePass == 0` | `ASSERT_FAIL` |
| 6 | `failReason` 含 `timeout`/`超时` | `TIMEOUT` |
| 7 | `failReason` 含 `model`/`模型调用失败` | `MODEL_ERROR` |
| 8 | `failReason` 含 `JSON`/`格式` | `PROTOCOL_ERROR` |
| 9 | 其他 | `OTHER` |

> 为什么顺序不能反：工具没选对时，参数一定是空的；先判参数会误报成"条件理解错"。

### 参考实现（可直接敲）

```java
@Override
public String classify(AgentEvalResultVO result) {
    if (result == null) {
        return OTHER;
    }
    if (Integer.valueOf(1).equals(result.getPassStatus())) {
        return NONE;
    }
    // 1. 工具选错：只要期望了工具但没选中，优先归这里
    if (StringUtils.hasText(result.getExpectedToolName())
            && Integer.valueOf(0).equals(result.getToolSelectPass())) {
        return TOOL_MISMATCH;
    }
    // 2. 工具选对、参数错
    if (Integer.valueOf(1).equals(result.getToolSelectPass())
            && Integer.valueOf(0).equals(result.getToolParamPass())) {
        return PARAM_MISS;
    }
    // 3. 需要资料但没命中
    if (result.getExpectedRagDocumentId() != null
            && Integer.valueOf(0).equals(result.getRagHitPass())) {
        return RETRIEVAL_EMPTY;
    }
    // 4. 答案不达标
    if (Integer.valueOf(0).equals(result.getAnswerKeywordPass())
            || Integer.valueOf(0).equals(result.getJudgePass())) {
        return ASSERT_FAIL;
    }
    // 5. 运行期原因
    String reason = result.getFailReason() == null ? "" : result.getFailReason().toLowerCase();
    if (reason.contains("timeout") || reason.contains("超时")) {
        return TIMEOUT;
    }
    if (reason.contains("model") || reason.contains("模型调用失败")) {
        return MODEL_ERROR;
    }
    if (reason.contains("json") || reason.contains("格式")) {
        return PROTOCOL_ERROR;
    }
    return OTHER;
}
```

### 单测（`EvalFailureClassifierTest`，至少 6 条）

| 输入 | 期望 |
|---|---|
| `passStatus=1` | `NONE` |
| `expectedToolName="JobSearchTool.searchJobs"`, `toolSelectPass=0` | `TOOL_MISMATCH` |
| `toolSelectPass=1`, `toolParamPass=0` | `PARAM_MISS` |
| `expectedRagDocumentId=9`, `ragHitPass=0` | `RETRIEVAL_EMPTY` |
| `answerKeywordPass=0`, 其他都过 | `ASSERT_FAIL` |
| `failReason="request timeout"` | `TIMEOUT` |
| 全空 | `OTHER` |

**验收**：单测全绿；报告页"失分点分布"不再出现大量"其他问题"。

---

## 练习 2：从逐用例结果重算指标（防止 run 聚合字段失真）

**新文件建议**：`quality/EvalMetricsCalculator.java`（类 + 静态方法即可，不需要接口）

**目标**：不信任 `agent_eval_run` 里存的聚合值，用逐用例结果重新计算 6 个指标，
和 run 上的值做交叉校验（差值 > 1% 打 WARN）。

### 定义

| 指标 | 公式 |
|---|---|
| 通过率 | `passStatus==1` 的条数 / 总条数 × 100 |
| 工具准确率 | `toolSelectPass==1` / **有期望工具**的条数 × 100 |
| 参数准确率 | `toolParamPass==1` / **工具选对**的条数 × 100 |
| 资料命中率 | `ragHitPass==1` / **期望了 RAG** 的条数 × 100 |
| 答案达标率 | `answerKeywordPass==1` / 总条数 × 100 |
| 平均耗时 | `costTime` 求和 / 条数 |

> 注意分母：工具准确率不是除以总条数，否则"本来不需要工具"的题会把准确率拉低。

### 参考实现

```java
public final class EvalMetricsCalculator {

    public record Metrics(double passRate, double toolAccuracy, double paramAccuracy,
                          double ragHitRate, double answerRate, long avgCostTime) {
    }

    public static Metrics calculate(List<AgentEvalResultVO> cases) {
        if (cases == null || cases.isEmpty()) {
            return new Metrics(0, 0, 0, 0, 0, 0);
        }
        int total = cases.size();
        int pass = 0, toolBase = 0, toolPass = 0, paramBase = 0, paramPass = 0;
        int ragBase = 0, ragPass = 0, answerPass = 0;
        long cost = 0;

        for (AgentEvalResultVO item : cases) {
            if (Integer.valueOf(1).equals(item.getPassStatus())) pass++;
            if (StringUtils.hasText(item.getExpectedToolName())) {
                toolBase++;
                if (Integer.valueOf(1).equals(item.getToolSelectPass())) toolPass++;
            }
            if (Integer.valueOf(1).equals(item.getToolSelectPass())) {
                paramBase++;
                if (Integer.valueOf(1).equals(item.getToolParamPass())) paramPass++;
            }
            if (item.getExpectedRagDocumentId() != null) {
                ragBase++;
                if (Integer.valueOf(1).equals(item.getRagHitPass())) ragPass++;
            }
            if (Integer.valueOf(1).equals(item.getAnswerKeywordPass())) answerPass++;
            cost += item.getCostTime() == null ? 0 : item.getCostTime();
        }
        return new Metrics(
                percent(pass, total),
                percent(toolPass, toolBase),
                percent(paramPass, paramBase),
                percent(ragPass, ragBase),
                percent(answerPass, total),
                cost / total
        );
    }

    private static double percent(int numerator, int denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 10000.0 / denominator) / 100.0;
    }
}
```

### 单测

- 空列表 → 全 0；
- 3 条：1 条通过、1 条工具错、1 条参数错 → `passRate=33.33`、`toolAccuracy=66.67`、`paramAccuracy=50`（分母是"工具选对"的 2 条中的 1 条）；
- 无 RAG 期望时 `ragHitRate=0`，不抛异常。

**验收**：报告页指标与 run 表一致（误差 ≤ 1%）；单测覆盖空列表、全通过、混合三种情况。

---

## 练习 3：基线对比 + 门禁判定

**新文件建议**：`quality/EvalGateEvaluator.java`

**目标**：新批次跑完，自动和"基线批次"比，输出 `PASS` / `BLOCK` 与一句人话结论。

### 规则

```
通过率下降 > 2 个百分点        → BLOCK
工具准确率下降 > 5 个百分点    → BLOCK
参数准确率下降 > 5 个百分点    → BLOCK
否则                          → PASS
```

### 参考实现

```java
public final class EvalGateEvaluator {

    public record GateResult(boolean pass, String reason,
                             double passRateDelta, double toolDelta, double paramDelta) {
    }

    public static GateResult evaluate(AgentEvalRunVO current, AgentEvalRunVO baseline) {
        if (current == null || baseline == null) {
            return new GateResult(true, "没有基线，本次不阻断", 0, 0, 0);
        }
        double passDelta = diff(current.getPassCount(), current.getTotalCount(),
                baseline.getPassCount(), baseline.getTotalCount());
        double toolDelta = diff(current.getToolAccuracy(), baseline.getToolAccuracy());
        double paramDelta = diff(current.getParamAccuracy(), baseline.getParamAccuracy());

        boolean blocked = passDelta < -2 || toolDelta < -5 || paramDelta < -5;
        String reason = blocked
                ? String.format("质量下降：通过率 %+.1f%%、工具 %+.1f%%、参数 %+.1f%%", passDelta, toolDelta, paramDelta)
                : "与基线持平或更好，允许合入";
        return new GateResult(!blocked, reason, passDelta, toolDelta, paramDelta);
    }

    private static double diff(Number current, Number baseline) {
        return round(current == null ? 0 : current.doubleValue())
                - round(baseline == null ? 0 : baseline.doubleValue());
    }

    private static double diff(int passC, int totalC, int passB, int totalB) {
        return round(totalC == 0 ? 0 : passC * 100.0 / totalC)
                - round(totalB == 0 ? 0 : passB * 100.0 / totalB);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
```

### 单测

| 场景 | 期望 |
|---|---|
| 无基线 | `pass=true`，reason 含"没有基线" |
| 通过率 80% → 75% | `pass=false` |
| 通过率 80% → 79%，工具 90% → 88% | `pass=true`（未超阈值） |
| 参数 90% → 84% | `pass=false` |

**验收**：批次详情页展示 `PASS/BLOCK` 徽标 + 差值（正绿负红）。

---

## 交付要求（你写完后）

1. `SimpleEvalFailureClassifier` 的 `TODO(练习)` 删掉，换成完整优先级；
2. 新增 `EvalMetricsCalculator` 与 `EvalGateEvaluator` + 对应单测；
3. 跑 `mvn -pl Job-Agent-Bootstrap -am test`，保证 378+ 全绿；
4. 在报告接口里接入：`EvalBatchReportServiceImpl.report()` 增加 `metrics` 与 `gate` 字段（VO 已预留位置，自行扩展）。

## 提示

- 所有判断都用 `Integer.valueOf(1).equals(...)`，不要用 `==`，避免空指针拆箱；
- 分母为 0 时统一返回 0，不要返回 NaN；
- 阈值写成常量（`PASS_RATE_DROP_LIMIT = 2`），不要散落在 if 里。
