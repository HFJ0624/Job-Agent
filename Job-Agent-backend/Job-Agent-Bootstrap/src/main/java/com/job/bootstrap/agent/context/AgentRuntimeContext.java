package com.job.bootstrap.agent.context;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 作者: hfj
 * 功能: Agent 单次请求运行时上下文
 * 设计说明:
 * 1. Agent 调用工具时，工具方法内部不能依赖大模型传 userId。
 *    因为 userId 属于后端登录态，不能让大模型生成，否则存在越权风险。
 * 2. 一次用户对话中，除了 userId，还需要记录 conversationId、traceId、intentCode。
 *    这些字段可以让工具调用日志和本次对话关联起来。
 * 3. 使用 ThreadLocal 是因为 LangChain4j 调用工具时，本质还是在当前请求线程里执行 Java 方法。
 *    这样工具类可以通过 AgentRuntimeContext 获取当前用户和当前会话。
 * 4. 使用完必须 clear()，否则 Tomcat 线程池复用时，可能出现 A 用户的上下文残留到 B 用户请求里。
 */
public class AgentRuntimeContext {

    /**
     * ThreadLocal 保存当前请求上下文。
     * 每个请求线程都有自己独立的一份 Context。
     */
    private static final ThreadLocal<Context> CONTEXT_HOLDER = new ThreadLocal<>();

    private AgentRuntimeContext() {
    }

    /**
     * 设置当前 Agent 请求上下文。
     *
     * @param userId 当前登录用户ID
     * @param conversationId 当前会话ID
     * @param traceId 当前对话链路ID
     * @param intentCode 当前识别出来的用户意图
     */
    public static void set(Long userId, Long conversationId, String traceId, String intentCode) {
        set(userId, conversationId, traceId, intentCode, Set.of());
    }

    /**
     * 设置当前 Agent 请求上下文。
     *
     * @param userId 当前登录用户ID
     * @param conversationId 当前会话ID
     * @param traceId 当前对话链路ID
     * @param intentCode 当前识别出来的用户意图
     * @param confirmedToolNames 本轮用户已确认允许执行的工具名
     */
    public static void set(
            Long userId,
            Long conversationId,
            String traceId,
            String intentCode,
            Collection<String> confirmedToolNames
    ) {
        Context context = new Context();
        context.setUserId(userId);
        context.setConversationId(conversationId);
        context.setTraceId(traceId);
        context.setIntentCode(intentCode);
        context.setConfirmedToolNames(confirmedToolNames);
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取当前上下文。
     *
     * @return 当前线程上下文
     */
    public static Context get() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取当前上下文，如果不存在则抛异常。
     * 工具方法里建议用这个方法，因为工具必须在 Agent 请求中执行。
     *
     * @return 当前线程上下文
     */
    public static Context getRequired() {
        Context context = CONTEXT_HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("Agent 运行时上下文不存在，请确认调用 Agent 前已设置上下文");
        }
        return context;
    }

    /**
     * 获取当前登录用户ID。
     *
     * @return 当前用户ID
     */
    public static Long getRequiredUserId() {
        return getRequired().getUserId();
    }

    /**
     * 获取当前会话ID。
     *
     * @return 当前会话ID
     */
    public static Long getConversationId() {
        Context context = CONTEXT_HOLDER.get();
        return context == null ? null : context.getConversationId();
    }

    /**
     * 获取当前 traceId。
     *
     * @return 当前 traceId
     */
    public static String getTraceId() {
        Context context = CONTEXT_HOLDER.get();
        return context == null ? null : context.getTraceId();
    }

    /**
     * 获取当前意图编码。
     *
     * @return 当前意图编码
     */
    public static String getIntentCode() {
        Context context = CONTEXT_HOLDER.get();
        return context == null ? null : context.getIntentCode();
    }

    /**
     * 判断指定工具是否已经获得用户确认。
     *
     * @param toolName 工具名
     * @return true 表示本轮请求已确认
     */
    public static boolean isToolConfirmed(String toolName) {
        Context context = CONTEXT_HOLDER.get();
        return context != null && context.isToolConfirmed(toolName);
    }

    /**
     * 设置当前正在执行的计划步骤。
     *
     * 说明:
     * 1. Executor 每执行一个 step 前会调用本方法。
     * 2. Tool 本身不直接依赖 planId/stepId，避免业务工具和编排表强耦合。
     * 3. AgentToolGuard 会读取这两个字段写入 Trace，方便后台串起计划和工具调用。
     *
     * @param planId 当前计划ID
     * @param stepId 当前步骤ID
     */
    public static void setCurrentPlanStep(Long planId, Long stepId) {
        Context context = getRequired();
        context.setPlanId(planId);
        context.setStepId(stepId);
    }

    /**
     * 清理当前步骤上下文。
     * Executor 执行完一个步骤后调用，避免后续非步骤工具调用误带旧 stepId。
     */
    public static void clearCurrentPlanStep() {
        Context context = CONTEXT_HOLDER.get();
        if (context != null) {
            context.setPlanId(null);
            context.setStepId(null);
        }
    }

    /**
     * 清理上下文。
     * 注意:
     * 1. 这个方法必须在 finally 中调用。
     * 2. 不清理会导致线程复用时用户数据串号。
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 单次 Agent 请求上下文对象。
     * 这里不用 Lombok，避免公共基础类过度依赖注解。
     */
    public static class Context {

        /**
         * 当前登录用户ID。
         */
        private Long userId;

        /**
         * 当前 AI 会话ID。
         */
        private Long conversationId;

        /**
         * 当前请求链路ID。
         */
        private String traceId;

        /**
         * 当前用户意图编码。
         */
        private String intentCode;

        /**
         * 当前执行中的 Agent 计划ID。
         */
        private Long planId;

        /**
         * 当前执行中的 Agent 计划步骤ID。
         */
        private Long stepId;

        /**
         * 本轮用户已确认允许执行的工具名。
         * 这里保存工具唯一名称，例如 GreetingGenerateTool.generateGreeting。
         */
        private Set<String> confirmedToolNames = Set.of();

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getIntentCode() {
            return intentCode;
        }

        public void setIntentCode(String intentCode) {
            this.intentCode = intentCode;
        }

        public Long getPlanId() {
            return planId;
        }

        public void setPlanId(Long planId) {
            this.planId = planId;
        }

        public Long getStepId() {
            return stepId;
        }

        public void setStepId(Long stepId) {
            this.stepId = stepId;
        }

        public Set<String> getConfirmedToolNames() {
            return confirmedToolNames;
        }

        public void setConfirmedToolNames(Collection<String> confirmedToolNames) {
            if (confirmedToolNames == null || confirmedToolNames.isEmpty()) {
                this.confirmedToolNames = Set.of();
                return;
            }

            Set<String> values = new HashSet<>();
            for (String toolName : confirmedToolNames) {
                if (toolName != null && !toolName.isBlank()) {
                    values.add(toolName.trim());
                }
            }
            this.confirmedToolNames = values;
        }

        public boolean isToolConfirmed(String toolName) {
            return toolName != null && confirmedToolNames.contains(toolName.trim());
        }
    }
}
