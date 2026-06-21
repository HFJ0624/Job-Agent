package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.ai.AiModelCircuitState;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:AI 模型熔断状态 Mapper
 * 日期:2026/6/21
 */
@Mapper
public interface AiModelCircuitStateMapper extends BaseMapper<AiModelCircuitState> {
}
