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
        // TODO(练习 1)：按 docs/EVAL_CORE_CODE.md 实现完整优先级规则。
        // 当前最小实现：只区分"通过/未归因"，保证报告能跑起来。
        if (result != null && Integer.valueOf(1).equals(result.getPassStatus())) {
            return NONE;
        }
        return OTHER;
    }
}
