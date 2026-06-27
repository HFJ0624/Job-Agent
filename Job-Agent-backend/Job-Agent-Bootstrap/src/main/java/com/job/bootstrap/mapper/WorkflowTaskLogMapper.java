package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.workflow.WorkflowTaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流任务阶段日志 Mapper。
 */
@Mapper
public interface WorkflowTaskLogMapper extends BaseMapper<WorkflowTaskLog> {
}
