package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.ai.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:AI 模型配置 Mapper
 * 日期:2026/6/21
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
