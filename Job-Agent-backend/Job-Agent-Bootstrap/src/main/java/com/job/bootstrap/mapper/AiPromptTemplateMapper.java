package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.ai.AiPromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:AI Prompt 模板 Mapper
 * 日期:2026/6/21
 */
@Mapper
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplate> {
}
