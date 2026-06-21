package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.rag.RagChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:RAG 切块 Mapper
 * 日期:2026/6/20
 */
@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {
}
