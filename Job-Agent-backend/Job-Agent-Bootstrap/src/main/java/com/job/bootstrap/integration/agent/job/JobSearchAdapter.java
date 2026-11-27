package com.job.bootstrap.integration.agent.job;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.agent.port.JobSearchPort;
import com.job.bootstrap.service.JobPositionService;
import com.job.common.entity.position.JobPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 功能: Agent 岗位搜索端口的宿主实现（Bootstrap 侧适配器）。
 *
 * <p>把 Infra-Ai 定义的窄端口映射到现有 {@link JobPositionService}，
 * 岗位筛选、分页、排序、权限与数据查询全部沿用原实现，不复制业务逻辑。</p>
 *
 * <p>管理端特有筛选项（公司、区域、类别、工作类型、状态）在此显式传 null，
 * 保证 Agent 的查询范围与旧行为一致。</p>
 */
@Component
@RequiredArgsConstructor
public class JobSearchAdapter implements JobSearchPort {

    private final JobPositionService jobPositionService;

    @Override
    public IPage<JobPosition> pagePositions(Long pageNo, Long pageSize, String keyword, String city,
                                            String educationReq, String experienceReq, boolean onlyPublished) {
        return jobPositionService.pagePositions(
                pageNo, pageSize, keyword,
                null, city, null, null,
                educationReq, experienceReq,
                null, null, onlyPublished);
    }

    @Override
    public JobPosition findById(Long jobId) {
        return jobPositionService.getById(jobId);
    }
}
