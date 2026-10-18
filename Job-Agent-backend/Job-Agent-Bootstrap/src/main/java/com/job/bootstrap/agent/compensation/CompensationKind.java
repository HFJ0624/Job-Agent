package com.job.bootstrap.agent.compensation;

/**
 * 功能: 补偿动作类型（任务 G9）。
 *
 * 设计说明:
 * 1. COMPENSATABLE：有确定性的反向操作，且幂等（重复执行无害）；
 * 2. IRREVERSIBLE：副作用已触达外部世界（如邮件已发出），无法自动撤回，
 *    只能记录事实并如实告知用户；
 * 3. NO_ACTION：副作用是站内分析档案（评分/匹配记录），删除会丢审计、保留无害，
 *    明确“不补偿”也是一种决策，必须留事件而非静默。
 */
public enum CompensationKind {

    /** 可补偿：执行确定性反向操作。 */
    COMPENSATABLE,

    /** 不可逆：外部动作已生效，只能记录与告知。 */
    IRREVERSIBLE,

    /** 无需补偿：档案类副作用，保留不删。 */
    NO_ACTION
}
