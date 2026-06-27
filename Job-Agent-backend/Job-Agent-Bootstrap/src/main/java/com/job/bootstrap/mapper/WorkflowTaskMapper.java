package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.workflow.WorkflowTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流任务 Mapper。
 */
@Mapper
public interface WorkflowTaskMapper extends BaseMapper<WorkflowTask> {
}
