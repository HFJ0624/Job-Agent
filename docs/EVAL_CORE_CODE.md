# Eval 平台 · 核心代码（照此实现即闭环）

> 用法：打开对应文件 → **整段替换**标了 `TODO(练习)` 的方法 → 跑测试。
> 三个类互相独立，建议按 1 → 2 → 3 顺序做，每做完一个跑一次 `mvn test`。
>
> 仓库里已留好挂载点，**不需要改其它文件**：
> - 分类器由 `EvalBatchReportServiceImpl` 注入调用；
> - `EvalMetricsCalculator` / `EvalGateEvaluator` 返回 `null` 时报告自动回退，返回结果即自动接管。

---

## 练习 1：失败归因

**文件**：`Job-Agent-Bootstrap/src/main/java/com/job/bootstrap/application/agent/service/quality/SimpleEvalFailureClassifier.java`

**作用**：把一条失败结果归到唯一分类；报告页的"主要失分点"就是按它聚合的。

### 完整代码（整段替换 `classify` 方法）

```java
    /**
     * 把单条评测结果归因到一个固定分类。
     *
     * <p>判定顺序很重要：工具没选对时参数一定为空，如果先判参数，
     * 会把"工具选错"误报成"条件理解错"，报告结论就全歪了。</p>
     *
     * @param result 单条评测结果，可能为 null
     * @return 分类编码；通过返回 {@link #NONE}
     */
    @Override
    public String classify(AgentEvalResultVO result) {
        // 防御：上游可能传空
        if (result == null) {
            return OTHER;
        }

        // 1. 已通过：直接返回 NONE，不参与失败统计
        if (Integer.valueOf(1).equals(result.getPassStatus())) {
            return NONE;
        }

        // 2. 工具选错：期望了某个工具，但实际没选中
        //    注意用 hasText 判断"有没有期望"，避免无工具用例被误判
        if (StringUtils.hasText(result.getExpectedToolName())
                && Integer.valueOf(0).equals(result.getToolSelectPass())) {
            return TOOL_MISMATCH;
        }

        // 3. 工具选对、参数错：这才是"条件理解错"
        if (Integer.valueOf(1).equals(result.getToolSelectPass())
                && Integer.valueOf(0).equals(result.getToolParamPass())) {
            return PARAM_MISS;
        }

        // 4. 需要资料但没命中：只有期望了 RAG 文档的用例才算
        if (result.getExpectedRagDocumentId() != null
                && Integer.valueOf(0).equals(result.getRagHitPass())) {
            return RETRIEVAL_EMPTY;
        }

        // 5. 答案不达标：关键词缺失或 Judge 判不过
        if (Integer.valueOf(0).equals(result.getAnswerKeywordPass())
                || Integer.valueOf(0).equals(result.getJudgePass())) {
            return ASSERT_FAIL;
        }

        // 6. 运行期原因：从失败描述里兜底识别
        String reason = result.getFailReason() == null
                ? "" : result.getFailReason().toLowerCase();
        if (reason.contains("timeout") || reason.contains("超时")) {
            return TIMEOUT;
        }
        if (reason.contains("model") || reason.contains("模型调用失败")) {
            return MODEL_ERROR;
        }
        if (reason.contains("json") || reason.contains("格式")) {
            return PROTOCOL_ERROR;
        }

        // 7. 都不匹配：保底分类，便于后续人工排查
        return OTHER;
    }
```

### 单测（新建 `SimpleEvalFailureClassifierTest`）

```java
package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalResultVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 功能: 失败归因的优先级与边界验证。
 */
class SimpleEvalFailureClassifierTest {

    private final SimpleEvalFailureClassifier classifier = new SimpleEvalFailureClassifier();

    private AgentEvalResultVO result(Integer passStatus) {
        AgentEvalResultVO vo = new AgentEvalResultVO();
        vo.setPassStatus(passStatus);
        return vo;
    }

    @Test
    void shouldReturnNoneWhenPassed() {
        assertEquals(EvalFailureClassifier.NONE, classifier.classify(result(1)));
    }

    @Test
    void shouldReturnToolMismatchWhenExpectedToolNotSelected() {
        AgentEvalResultVO vo = result(0);
        vo.setExpectedToolName("JobSearchTool.searchJobs");
        vo.setToolSelectPass(0);
        assertEquals(EvalFailureClassifier.TOOL_MISMATCH, classifier.classify(vo));
    }

    @Test
    void shouldReturnParamMissWhenToolSelectedButParamWrong() {
        AgentEvalResultVO vo = result(0);
        vo.setToolSelectPass(1);
        vo.setToolParamPass(0);
        assertEquals(EvalFailureClassifier.PARAM_MISS, classifier.classify(vo));
    }

    @Test
    void shouldReturnRetrievalEmptyOnlyWhenRagExpected() {
        AgentEvalResultVO vo = result(0);
        vo.setToolSelectPass(1);
        vo.setToolParamPass(1);
        vo.setExpectedRagDocumentId(9L);
        vo.setRagHitPass(0);
        assertEquals(EvalFailureClassifier.RETRIEVAL_EMPTY, classifier.classify(vo));
    }

    @Test
    void shouldReturnAssertFailWhenKeywordMissing() {
        AgentEvalResultVO vo = result(0);
        vo.setToolSelectPass(1);
        vo.setToolParamPass(1);
        vo.setAnswerKeywordPass(0);
        assertEquals(EvalFailureClassifier.ASSERT_FAIL, classifier.classify(vo));
    }

    @Test
    void shouldReturnTimeoutFromReason() {
        AgentEvalResultVO vo = result(0);
        vo.setToolSelectPass(1);
        vo.setToolParamPass(1);
        vo.setAnswerKeywordPass(1);
        vo.setFailReason("request timeout after 300s");
        assertEquals(EvalFailureClassifier.TIMEOUT, classifier.classify(vo));
    }

    @Test
    void shouldReturnOtherWhenNothingMatches() {
        assertEquals(EvalFailureClassifier.OTHER, classifier.classify(result(0)));
    }
}
```

**验收**：7 条全绿；页面"主要失分点"出现"用错了工具 / 条件理解错了 / 没检索到资料"等中文分类。

---

## 练习 2：指标重算

**文件**：`quality/EvalMetricsCalculator.java`

**作用**：不信任 `agent_eval_run` 里存的聚合值，用逐用例结果重算，交叉校验是否失真。

### 完整代码（整段替换 `calculate` 方法）

```java
    /**
     * 从逐用例结果重算整批指标。
     *
     * <p>关键点：三个准确率的分母不同——工具看"有期望工具的题"，
     * 参数看"工具选对的题"，RAG 看"期望了资料的题"。用总数当分母会失真。</p>
     *
     * @param cases 该批次全部结果，可能为 null/空
     * @return 指标快照；空批次返回全 0，不返回 null
     */
    public static Metrics calculate(List<AgentEvalResultVO> cases) {
        if (cases == null || cases.isEmpty()) {
            return new Metrics(0, 0, 0, 0, 0, 0);
        }

        int total = cases.size();
        int pass = 0;

        // 三个准确率各自的分子/分母
        int toolBase = 0, toolPass = 0;
        int paramBase = 0, paramPass = 0;
        int ragBase = 0, ragPass = 0;
        int answerPass = 0;

        long cost = 0;

        for (AgentEvalResultVO item : cases) {
            if (item == null) {
                continue;
            }
            if (Integer.valueOf(1).equals(item.getPassStatus())) {
                pass++;
            }
            if (StringUtils.hasText(item.getExpectedToolName())) {
                toolBase++;
                if (Integer.valueOf(1).equals(item.getToolSelectPass())) {
                    toolPass++;
                }
            }
            if (Integer.valueOf(1).equals(item.getToolSelectPass())) {
                paramBase++;
                if (Integer.valueOf(1).equals(item.getToolParamPass())) {
                    paramPass++;
                }
            }
            if (item.getExpectedRagDocumentId() != null) {
                ragBase++;
                if (Integer.valueOf(1).equals(item.getRagHitPass())) {
                    ragPass++;
                }
            }
            if (Integer.valueOf(1).equals(item.getAnswerKeywordPass())) {
                answerPass++;
            }
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

    /**
     * 百分比：保留 2 位；分母为 0 返回 0，避免 NaN 传到前端。
     */
    private static double percent(int numerator, int denominator) {
        if (denominator == 0) {
            return 0;
        }
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }
```

> 需要补两个 import：
> `import org.springframework.util.StringUtils;`、`import java.util.List;`

### 单测（新建 `EvalMetricsCalculatorTest`）

```java
package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 功能: 指标口径验证（重点在三个不同分母）。
 */
class EvalMetricsCalculatorTest {

    private AgentEvalResultVO item(int pass, int toolSelect, int toolParam,
                                   Long ragDocId, int ragHit, int keyword, long cost) {
        AgentEvalResultVO vo = new AgentEvalResultVO();
        vo.setPassStatus(pass);
        vo.setToolSelectPass(toolSelect);
        vo.setToolParamPass(toolParam);
        vo.setExpectedRagDocumentId(ragDocId);
        vo.setRagHitPass(ragHit);
        vo.setAnswerKeywordPass(keyword);
        vo.setCostTime(cost);
        return vo;
    }

    @Test
    void shouldReturnZerosForEmptyInput() {
        EvalMetricsCalculator.Metrics metrics = EvalMetricsCalculator.calculate(List.of());
        assertEquals(0, metrics.passRate());
        assertEquals(0, metrics.avgCostTime());
    }

    @Test
    void shouldUseDifferentDenominators() {
        // 3 条：1 通过；1 条工具错；1 条工具对但参数错
        AgentEvalResultVO passed = item(1, 1, 1, null, 0, 1, 100);
        passed.setExpectedToolName("JobSearchTool.searchJobs");

        AgentEvalResultVO toolWrong = item(0, 0, 0, null, 0, 0, 200);
        toolWrong.setExpectedToolName("ResumeAnalyzeTool.analyzeResume");

        AgentEvalResultVO paramWrong = item(0, 1, 0, null, 0, 0, 300);
        paramWrong.setExpectedToolName("JobSearchTool.searchJobs");

        EvalMetricsCalculator.Metrics metrics =
                EvalMetricsCalculator.calculate(List.of(passed, toolWrong, paramWrong));

        assertEquals(33.33, metrics.passRate(), 0.01);       // 1/3
        assertEquals(66.67, metrics.toolAccuracy(), 0.01);   // 2/3（分母=有期望工具的 3 条）
        assertEquals(50.0, metrics.paramAccuracy(), 0.01);   // 1/2（分母=工具选对 2 条）
        assertEquals(200, metrics.avgCostTime());            // (100+200+300)/3
    }
}
```

**验收**：单测全绿；报告接口 `metrics` 字段非空，且与页面指标误差 ≤1%。

---

## 练习 3：基线对比与门禁

**文件**：`quality/EvalGateEvaluator.java`

**作用**：新批次和基线比，给出 PASS/BLOCK 和一句人话结论。

### 阈值（写成常量，不要散落）

| 指标 | 允许下降 |
|---|---|
| 通过率 | 2 个百分点 |
| 工具准确率 | 5 个百分点 |
| 参数准确率 | 5 个百分点 |

### 完整代码（整段替换 `evaluate` 方法，并补私有方法）

```java
    /** 通过率允许下降的百分点。 */
    private static final double PASS_RATE_DROP_LIMIT = 2.0;
    /** 工具准确率允许下降的百分点。 */
    private static final double TOOL_DROP_LIMIT = 5.0;
    /** 参数准确率允许下降的百分点。 */
    private static final double PARAM_DROP_LIMIT = 5.0;

    /**
     * 对比当前批次与基线批次。
     *
     * <p>没有基线时一律 PASS（首次评测不应该被阻断），
     * 但 reason 要写清楚"本次不阻断"，让用户知道不是"质量没问题"。</p>
     *
     * @param current  当前批次
     * @param baseline 基线批次，可为 null
     */
    public static GateResult evaluate(AgentEvalRunVO current, AgentEvalRunVO baseline) {
        if (current == null) {
            return new GateResult(true, "没有可判定的批次", 0, 0, 0);
        }
        if (baseline == null) {
            return new GateResult(true, "首次评测，没有基线，本次不阻断", 0, 0, 0);
        }

        double passDelta = diffRate(current.getPassCount(), current.getTotalCount(),
                baseline.getPassCount(), baseline.getTotalCount());
        double toolDelta = diff(current.getToolAccuracy(), baseline.getToolAccuracy());
        double paramDelta = diff(current.getParamAccuracy(), baseline.getParamAccuracy());

        // 任意一项跌破阈值即阻断；三个条件是"或"关系
        boolean blocked = passDelta < -PASS_RATE_DROP_LIMIT
                || toolDelta < -TOOL_DROP_LIMIT
                || paramDelta < -PARAM_DROP_LIMIT;

        String reason = blocked
                ? String.format("质量下降超过阈值：通过率 %+.1f%%、工具 %+.1f%%、参数 %+.1f%%",
                        passDelta, toolDelta, paramDelta)
                : String.format("与基线持平或更好（通过率 %+.1f%%、工具 %+.1f%%、参数 %+.1f%%）",
                        passDelta, toolDelta, paramDelta);

        return new GateResult(!blocked, reason, passDelta, toolDelta, paramDelta);
    }

    /** 通过率差值：两边各自算百分比再相减，避免总数不同导致误判。 */
    private static double diffRate(Integer currentPass, Integer currentTotal,
                                   Integer baselinePass, Integer baselineTotal) {
        double currentRate = currentTotal == null || currentTotal == 0
                ? 0 : currentPass * 100.0 / currentTotal;
        double baselineRate = baselineTotal == null || baselineTotal == 0
                ? 0 : baselinePass * 100.0 / baselineTotal;
        return round(currentRate - baselineRate);
    }

    /** 数值型指标差值（空按 0 处理）。 */
    private static double diff(Number current, Number baseline) {
        return round(value(current) - value(baseline));
    }

    private static double value(Number number) {
        return number == null ? 0 : number.doubleValue();
    }

    /** 统一保留 2 位，避免 0.30000000000000004 这种浮点噪声。 */
    private static double round(double number) {
        return Math.round(number * 100) / 100.0;
    }
```

### 单测（新建 `EvalGateEvaluatorTest`）

```java
package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalRunVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 功能: 门禁阈值边界验证。
 */
class EvalGateEvaluatorTest {

    private AgentEvalRunVO run(int pass, int total, double tool, double param) {
        AgentEvalRunVO vo = new AgentEvalRunVO();
        vo.setPassCount(pass);
        vo.setTotalCount(total);
        vo.setToolAccuracy(BigDecimal.valueOf(tool));
        vo.setParamAccuracy(BigDecimal.valueOf(param));
        return vo;
    }

    @Test
    void shouldPassWhenNoBaseline() {
        assertTrue(EvalGateEvaluator.evaluate(run(8, 10, 90, 90), null).pass());
    }

    @Test
    void shouldBlockWhenPassRateDropsMoreThanTwoPoints() {
        // 80% -> 75%，下降 5 个点，超过 2
        assertFalse(EvalGateEvaluator.evaluate(run(75, 100, 90, 90), run(80, 100, 90, 90)).pass());
    }

    @Test
    void shouldPassWhenDropsWithinThreshold() {
        // 通过率降 1 个点、工具降 2 个点，都未超阈值
        assertTrue(EvalGateEvaluator.evaluate(run(79, 100, 88, 90), run(80, 100, 90, 90)).pass());
    }

    @Test
    void shouldBlockWhenParamDropsMoreThanFivePoints() {
        // 参数 90 -> 84，下降 6 个点
        assertFalse(EvalGateEvaluator.evaluate(run(80, 100, 90, 84), run(80, 100, 90, 90)).pass());
    }
}
```

**验收**：4 条全绿；批次报告出现 `PASS/BLOCK` 与差值（前端后续接徽标即可）。

---

## 接好线以后（不用你改）

`EvalBatchReportServiceImpl` 已经写好：

```java
// 指标：calculator 返回 null → 不设置，前端回退到 run 聚合值
attachMetrics(report, cases);
// 门禁：evaluator 返回 null → 不展示；返回结果则写入门禁字段
attachGate(report, run);
```

因此：

| 你的进度 | 页面表现 |
|---|---|
| 三个都没写 | 报告可用，指标来自 run，失败全是"其他" |
| 写完练习 1 | 失分点分类准确 |
| 写完练习 2 | 报告指标由逐用例重算 |
| 写完练习 3 | 报告出现 PASS/BLOCK |

## 自检清单

- [ ] 所有判断用 `Integer.valueOf(1).equals(...)`，不用 `==`（防空指针拆箱）
- [ ] 分母为 0 返回 0，不产生 `NaN`
- [ ] 阈值写成常量
- [ ] 每个练习至少 4 条单测，且包含"空输入"与"边界值"
- [ ] `mvn -pl Job-Agent-Bootstrap -am test` 全绿

## 常见坑

1. **把参数准确率除以总题数** → 数值偏低，用户以为 AI 很差；
2. **先判参数后判工具** → 工具错全被归成参数错；
3. **没有基线时返回 BLOCK** → 第一次用就红一片，体验极差；
4. **用 `double` 直接比较差值** → `0.30000000000004` 这类噪声导致边界判定抖动，统一 `round` 两位。
