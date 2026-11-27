package com.job.common.vo.agent;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 功能: 评测失败分类统计项（报告页"失败原因"卡片/列表用）。
 */
@Data
public class EvalFailureCategoryVO {

    /** 分类编码，如 TOOL_MISMATCH。 */
    private String code;
    /** 面向非技术用户的中文说明。 */
    private String label;
    /** 该分类的用例数。 */
    private Integer count;
    /** 占全部失败用例的比例（0-100）。 */
    private BigDecimal ratio;
}
