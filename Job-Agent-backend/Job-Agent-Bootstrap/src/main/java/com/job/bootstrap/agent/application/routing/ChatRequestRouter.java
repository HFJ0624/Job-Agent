package com.job.bootstrap.agent.application.routing;

import com.job.bootstrap.agent.scenario.jobsearch.JobSearchReactEntry;
import com.job.bootstrap.agent.scenario.resume.ResumeReactEntry;
import com.job.bootstrap.agent.scenario.jobseek.entry.JobSeekReactEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        if (jobSeekReactEntry.shouldHandle(intentCode, planId, message)) {
            return Route.MULTI_AGENT;
        }
        if (resumeReactEntry.shouldHandle(intentCode, planId, message)) {
            return Route.RESUME;
        }
        if (jobSearchReactEntry.shouldHandle(intentCode, planId)) {
            return Route.REACT_SEARCH;
        }
        return Route.LEGACY;
    }
}
