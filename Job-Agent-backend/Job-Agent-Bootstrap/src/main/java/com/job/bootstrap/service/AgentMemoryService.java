package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentMemoryQueryDTO;
import com.job.common.entity.agent.AgentLongTermMemory;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.enums.AgentMemoryType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆服务
 * 日期:2026/6/20
 */
public interface AgentMemoryService {

    /**
     * 保存或更新一条长期记忆。
     *
     * @param userId 用户 ID
     * @param memoryType 记忆类型
     * @param memoryKey 记忆键，有值时会更新同一用户同一键的旧记忆
     * @param memoryValue 记忆正文
     * @param summary 记忆摘要
     * @param sourceType 来源类型
     * @param sourceId 来源业务 ID
     * @param confidence 置信度
     * @param importance 重要性
     * @return 保存后的实体
     */
    AgentLongTermMemory saveOrUpdateMemory(
            Long userId,
            AgentMemoryType memoryType,
            String memoryKey,
            String memoryValue,
            String summary,
            String sourceType,
            Long sourceId,
            BigDecimal confidence,
            BigDecimal importance
    );

    /**
     * 检索当前用户的长期记忆。
     *
     * @param userId 用户 ID
     * @param query 检索词
     * @param limit 召回数量
     * @return 相关记忆
     */
    List<AgentMemoryVO> searchMemories(Long userId, String query, Integer limit);

    /**
     * 查询某个稳定记忆键的最新值。
     *
     * @param userId 用户 ID
     * @param memoryType 记忆类型
     * @param memoryKey 记忆键
     * @return 最新记忆值，不存在时返回 null
     */
    String findLatestMemoryValue(Long userId, AgentMemoryType memoryType, String memoryKey);

    /**
     * 后台分页查询长期记忆。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<AgentMemoryVO> pageMemories(AgentMemoryQueryDTO query);

    /**
     * 查询长期记忆详情。
     *
     * @param id 记忆 ID
     * @return 记忆详情
     */
    AgentMemoryVO getDetail(Long id);

    /**
     * 后台人工更新长期记忆状态。
     *
     * @param id 记忆 ID
     * @param status 目标状态，允许 ACTIVE、ARCHIVED、INVALID
     * @return 更新后的记忆
     */
    AgentMemoryVO updateStatus(Long id, String status);

    /**
     * 按记忆 key 归档当前用户的有效记忆。
     *
     * @param userId 用户 ID
     * @param memoryKeys 记忆 key 列表
     * @return 实际归档数量
     */
    int archiveActiveMemoriesByKeys(Long userId, List<String> memoryKeys);
}
