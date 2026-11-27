package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalResultVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 功能: 失败归因的基础实现（可按标志位粗分）。
 *
 */
@Component
public class SimpleEvalFailureClassifier implements EvalFailureClassifier {

    /***
     * 把单条评测结果归因到一个固定分类。
     *
     * @param result 评测结果
     * @return 分类编码；通过返回 {@link #NONE}
     */
    @Override
    public String classify(AgentEvalResultVO result) {

        //防御：上游可能传空
        if (result == null){
            return OTHER;
        }

        //1.已通过：直接返回 NONE，不参与失败统计
        if (Integer.valueOf(1).equals(result.getPassStatus())){
            return NONE;
        }

        //2.工具选错：期望了某个工具，但实际没选中
        if (StringUtils.hasText(result.getExpectedToolName()) && Integer.valueOf(0).equals(result.getPassStatus())){
            return TOOL_MISMATCH;
        }

        //3.工具选对、参数选错
        if (Integer.valueOf(1).equals(result.getToolSelectPass()) && Integer.valueOf(0).equals(result.getToolParamPass())) {
            return PARAM_MISS;
        }

        // 4. 需要资料但没命中：只有期望了 RAG 文档的用例才算
        if (result.getExpectedRagDocumentId() != null && Integer.valueOf(0).equals(result.getRagHitPass())) {
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
}
