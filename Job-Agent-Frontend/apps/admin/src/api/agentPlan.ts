import { request } from "./request";
import type {
    AgentPlanInfo,
    AgentPlanQuery,
    PageResult
} from "./types";

/**
 * 查询 Agent 计划分页。
 *
 * @param query 查询条件
 */
export function pageAgentPlans(query: AgentPlanQuery) {
    const params = new URLSearchParams();

    /*
     * 空值不传给后端，避免无意义筛选条件。
     */
    Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            params.append(key, String(value));
        }
    });

    return request<PageResult<AgentPlanInfo>>(
        `/admin/agent/plans/page?${params.toString()}`
    );
}

/**
 * 查询 Agent 计划详情。
 *
 * @param id 计划ID
 */
export function getAgentPlanDetail(id: number) {
    return request<AgentPlanInfo>(`/admin/agent/plans/${id}`);
}
