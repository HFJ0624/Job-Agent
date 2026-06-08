import { request } from "./request";
import type {
    AgentTraceLogInfo,
    AgentTraceLogQuery,
    PageResult
} from "./types";

/**
 * 查询 Agent Trace 日志分页。
 *
 * @param query 查询条件
 */
export function pageAgentTraceLogs(query: AgentTraceLogQuery) {
    const params = new URLSearchParams();

    /*
     * 将查询对象转换成 query string。
     * 注意：空字符串不传，避免后端收到无意义条件。
     */
    Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            params.append(key, String(value));
        }
    });

    return request<PageResult<AgentTraceLogInfo>>(
        `/admin/agent/logs/page?${params.toString()}`
    );
}

/**
 * 查询 Agent Trace 日志详情。
 *
 * @param id 日志ID
 */
export function getAgentTraceLogDetail(id: number) {
    return request<AgentTraceLogInfo>(`/admin/agent/logs/${id}`);
}