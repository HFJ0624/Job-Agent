package com.job.bootstrap.agent.context;

/**
 * 作者:hfj
 * 功能:Agent 工具调用用户上下文
 * 说明:
 * 1. Agent 调用工具时，工具方法内部需要知道当前登录用户是谁。
 * 2. userId 不应该让大模型生成，而应该由后端登录态注入。
 * 3. ThreadLocal 可以在一次请求线程内保存当前 userId。
 * 4. 使用完必须 remove，避免线程池复用导致用户串号。
 * 日期: 2026/6/8 16:33
 */
public class AgentUserContext {

    /**
     * 保存当前请求对应的用户ID。
     */
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private AgentUserContext() {
    }

    /**
     * 设置当前登录用户ID。
     *
     * @param userId 当前登录用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前登录用户ID。
     *
     * @return 当前用户ID
     */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 获取当前登录用户ID，如果不存在则抛出异常。
     *
     * @return 当前用户ID
     */
    public static Long getRequiredUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("Agent 用户上下文不存在，请确认调用 Agent 前已设置 userId");
        }
        return userId;
    }

    /**
     * 清理当前线程中的用户ID。
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
