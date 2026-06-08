package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:AI 消息 Mapper
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
