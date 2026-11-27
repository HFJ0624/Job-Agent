package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalResultVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 功能: 失败归因的基础实现（可按标志位粗分）。
 *
 * <p>TODO(练习)：按 docs/EVAL_PRACTICE.md 的"练习 1"替换为完整优先级规则，
 * 并补 EvalFailureClassifierTest（至少 6 条用例）。</p>
 */
@Component
public class SimpleEvalFailureClassifier implements EvalFailureClassifier {

    @Override
    public String classify(AgentEvalResultVO result) {
        if (result == null) {
            return OTHER;
        }
        // 通过：passStatus=1
        if (Integer.valueOf(1).equals(result.getPassStatus())) {
            return NONE;
        }
        // 已有的归因优先（历史数据/上游已判定）
        if (StringUtils.hasText(result.getFailureType())) {
            return result.getFailureType();
        }
        // 基础兜底：按最直观的标志位粗分
        if (Integer.valueOf(0).equals(result.getToolSelectPass())) {
            return TOOL_MISMATCH;
        }
        if (Integer.valueOf(0).equals(result.getToolParamPass())) {
            return PARAM_MISS;
        }
        if (Integer.valueOf(0).equals(result.getRagHitPass())) {
            return RETRIEVAL_EMPTY;
        }
        if (Integer.valueOf(0).equals(result.getAnswerKeywordPass())) {
            return ASSERT_FAIL;
        }
        return OTHER;
    }
}
