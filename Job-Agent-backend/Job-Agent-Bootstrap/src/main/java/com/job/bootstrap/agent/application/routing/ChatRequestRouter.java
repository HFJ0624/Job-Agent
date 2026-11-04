package com.job.bootstrap.agent.application.routing;

import com.job.bootstrap.agent.application.task.AgentTaskService;
import com.job.bootstrap.agent.scenario.jobsearch.JobSearchReactEntry;
import com.job.bootstrap.agent.scenario.resume.ResumeReactEntry;
import com.job.bootstrap.agent.scenario.jobseek.entry.JobSeekReactEntry;
import com.job.common.entity.agent.AgentPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 功能: 对话请求路由（从 AgentChatServiceImpl 抽出）。
 *
 * <p>路由优先级集中在此，同步 chat 与流式 streamChat 共用，避免两份 if/else 逐渐分叉：</p>
 * <ol>
 *   <li>复合任务（已有 WORKFLOW_MULTI_AGENT 计划，或新请求同时包含简历+岗位意图）；</li>
 *   <li>独立简历分析（已有 WORKFLOW_RESUME 计划，或意图为 RESUME_ANALYZE 且开关开启）；</li>
 *   <li>独立岗位搜索（已有搜索 run，或意图 JOB_SEARCH 且开关开启）；</li>
 *   <li>其余交给旧模板链路。</li>
 * </ol>
 *
 * <p>说明：每个 Entry 的 shouldHandle 内部先按**持久化任务模式**判断，再看开关——
 * 因此"关开关后旧任务仍按原引擎执行"的语义在这里统一生效。</p>
 */
@Component
@RequiredArgsConstructor
public class ChatRequestRouter {

    /** 处理器类型。 */
    public enum Route {
        MULTI_AGENT,
        RESUME,
        REACT_SEARCH,
        LEGACY
    }

    private final AgentTaskService agentTaskService;
    private final JobSeekReactEntry jobSeekReactEntry;
    private final ResumeReactEntry resumeReactEntry;
    private final JobSearchReactEntry jobSearchReactEntry;

    /**
     * 决定本轮请求交给谁处理。
     *
     * @param intentCode 规则/LLM 识别出的意图编码
     * @param planId     已有计划 ID（可为空）
     * @param message    用户原始消息（复合意图判断需要）
     */
    public Route route(String intentCode, Long planId, String message) {
        /*
         * 已有任务：严格按持久化 runtimeMode 路由。
         * 不能再用关键词判断——例如 WORKFLOW_RESUME 的补参消息里出现"简历、岗位"时，
         * 若先走关键词匹配会被复合任务入口抢走，导致原任务无法续跑。
         */
        if (planId != null) {
            AgentPlan plan = agentTaskService.load(planId);
            if (plan == null || !StringUtils.hasText(plan.getRuntimeMode())) {
                return Route.LEGACY; // 任务不存在/无模式：交给旧链路按原逻辑处理（含归属校验）
            }
            return switch (plan.getRuntimeMode()) {
                case AgentTaskService.RUNTIME_MULTI_AGENT -> Route.MULTI_AGENT;
                case ResumeReactEntry.RUNTIME_RESUME -> Route.RESUME;
                case AgentTaskService.RUNTIME_REACT_SEARCH -> Route.REACT_SEARCH;
                default -> Route.LEGACY; // TEMPLATE 或其他历史模式
            };
        }

        // 新任务：意图 + 关键词 + 开关决定走哪条新链路，否则旧模板链路
        if (jobSeekReactEntry.shouldHandle(intentCode, null, message)) {
            return Route.MULTI_AGENT;
        }
        if (resumeReactEntry.shouldHandle(intentCode, null, message)) {
            return Route.RESUME;
        }
        if (jobSearchReactEntry.shouldHandle(intentCode, null)) {
            return Route.REACT_SEARCH;
        }
        return Route.LEGACY;
    }
}
