package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalResultVO;

/**
 * 功能: 把单条评测结果归因到一个固定的失败分类。
 *
 * <p>分类必须是有限枚举（而不是自由文本），报告才能聚合、筛选、对比。</p>
 *
 * <p>练习点：当前实现只做"取已有 failureType，缺失则 OTHER"的基础版；
 * 进阶归因规则与单测见 docs/EVAL_PRACTICE.md。</p>
 */
public interface EvalFailureClassifier {

    /** 通过（无失败）。 */
    String NONE = "NONE";
    /** 路由/意图没命中，走到了别的链路。 */
    String ROUTE_MISS = "ROUTE_MISS";
    /** 调用了错误的工具。 */
    String TOOL_MISMATCH = "TOOL_MISMATCH";
    /** 工具选对但关键参数不对。 */
    String PARAM_MISS = "PARAM_MISS";
    /** 检索为空或没命中期望文档。 */
    String RETRIEVAL_EMPTY = "RETRIEVAL_EMPTY";
    /** 答案不满足断言（关键词/质量分）。 */
    String ASSERT_FAIL = "ASSERT_FAIL";
    String MODEL_ERROR = "MODEL_ERROR";
    String TIMEOUT = "TIMEOUT";
    String PROTOCOL_ERROR = "PROTOCOL_ERROR";
    String OTHER = "OTHER";

    /**
     * 归因单条结果。
     *
     * @param result 评测结果
     * @return {@link #NONE} 表示通过；其余为失败分类
     */
    String classify(AgentEvalResultVO result);

    /** 分类的中文说明（给非技术用户看）。 */
    static String labelOf(String code) {
        return switch (code) {
            case NONE -> "通过";
            case ROUTE_MISS -> "没听懂需求（路由未命中）";
            case TOOL_MISMATCH -> "用错了工具";
            case PARAM_MISS -> "条件理解错了（参数不对）";
            case RETRIEVAL_EMPTY -> "没检索到资料";
            case ASSERT_FAIL -> "回答不符合要求";
            case MODEL_ERROR -> "模型调用失败";
            case TIMEOUT -> "执行超时";
            case PROTOCOL_ERROR -> "输出格式异常";
            default -> "其他问题";
        };
    }
}
