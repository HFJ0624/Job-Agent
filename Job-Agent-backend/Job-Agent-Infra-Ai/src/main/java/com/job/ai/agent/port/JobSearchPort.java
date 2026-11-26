package com.job.ai.agent.port;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.entity.position.JobPosition;

/**
 * 功能: Agent 岗位搜索端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>只暴露 Agent 真正需要的查询能力：分页 + 关键词 + 城市 + 学历/经验 + 仅已发布。
 * 公司、区域、岗位类别、工作类型、状态等管理端筛选不在此列，避免把整张岗位查询
 * 接口搬进 AI 模块。</p>
 *
 * <p>返回类型使用 MyBatis-Plus 的 {@link IPage} 与 Framework 实体，二者都在
 * Infra-Ai 的编译依赖内，因此端口可独立编译，不需要引用 Bootstrap。</p>
 */
public interface JobSearchPort {

    /**
     * 按 Agent 搜索条件分页查询岗位。
     *
     * @param pageNo        页码（调用方负责上限）
     * @param pageSize      页大小（调用方负责上限）
     * @param keyword       召回关键词（可空）
     * @param city          城市（可空）
     * @param educationReq  学历要求（可空）
     * @param experienceReq 经验要求（可空）
     * @param onlyPublished 是否只查已发布岗位
     */
    IPage<JobPosition> pagePositions(Long pageNo, Long pageSize, String keyword, String city,
                                     String educationReq, String experienceReq, boolean onlyPublished);
}
