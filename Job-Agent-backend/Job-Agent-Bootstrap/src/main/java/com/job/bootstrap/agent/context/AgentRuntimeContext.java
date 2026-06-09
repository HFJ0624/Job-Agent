package com.job.bootstrap.agent.context;

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
        Context context = new Context();
        context.setUserId(userId);
        context.setConversationId(conversationId);
        context.setTraceId(traceId);
        context.setIntentCode(intentCode);
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
    }
}
