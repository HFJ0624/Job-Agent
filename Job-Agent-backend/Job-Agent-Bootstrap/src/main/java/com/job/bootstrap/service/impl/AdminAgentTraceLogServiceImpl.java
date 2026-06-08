package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.service.AdminAgentTraceLogService;
import com.job.common.dto.agent.AgentTraceLogQueryDTO;
import com.job.common.entity.agent.AgentTraceLog;
import com.job.common.vo.agent.AgentTraceLogVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 作者:hfj
 * 功能:后台 Agent Trace 日志服务实现
 * 日期: 2026/6/8 20:05
 */
@Service
@RequiredArgsConstructor
public class AdminAgentTraceLogServiceImpl implements AdminAgentTraceLogService {

    private static final int NOT_DELETED = 0;

    private final AgentTraceLogMapper agentTraceLogMapper;

    /**
     * 分页查询 Agent Trace 日志。
     */
    @Override
    public IPage<AgentTraceLogVO> pageLogs(AgentTraceLogQueryDTO query) {
        /*
         * 1. 防御性处理分页参数，避免前端传空或传负数。
         */
        long pageNum = query.getPageNum() == null || query.getPageNum() <= 0
                ? 1L
                : query.getPageNum();

        long pageSize = query.getPageSize() == null || query.getPageSize() <= 0
                ? 10L
                : query.getPageSize();

        /*
         * 2. 限制最大 pageSize，避免一次查太多日志影响后台性能。
         */
        if (pageSize > 100) {
            pageSize = 100;
        }

        Page<AgentTraceLog> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<AgentTraceLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTraceLog::getIsDeleted, NOT_DELETED);

        /*
         * 3. 根据查询条件动态拼接 SQL。
         */
        if (StringUtils.hasText(query.getTraceId())) {
            wrapper.like(AgentTraceLog::getTraceId, query.getTraceId().trim());
        }

        if (query.getUserId() != null) {
            wrapper.eq(AgentTraceLog::getUserId, query.getUserId());
        }

        if (query.getConversationId() != null) {
            wrapper.eq(AgentTraceLog::getConversationId, query.getConversationId());
        }

        if (StringUtils.hasText(query.getIntentCode())) {
            wrapper.eq(AgentTraceLog::getIntentCode, query.getIntentCode().trim());
        }

        if (StringUtils.hasText(query.getToolName())) {
            wrapper.like(AgentTraceLog::getToolName, query.getToolName().trim());
        }

        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AgentTraceLog::getStatus, query.getStatus().trim());
        }

        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(AgentTraceLog::getCreateTime, query.getStartTime().trim());
        }

        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(AgentTraceLog::getCreateTime, query.getEndTime().trim());
        }

        /*
         * 4. 新日志排在前面，方便后台排查最近问题。
         */
        wrapper.orderByDesc(AgentTraceLog::getCreateTime);

        IPage<AgentTraceLog> entityPage = agentTraceLogMapper.selectPage(page, wrapper);

        /*
         * 5. 将 Entity 分页转换为 VO 分页。
         */
        return entityPage.convert(AgentTraceLogVO::from);
    }

    /**
     * 查询 Trace 日志详情。
     */
    @Override
    public AgentTraceLogVO getDetail(Long id) {
        AgentTraceLog log = agentTraceLogMapper.selectById(id);

        if (log == null || log.getIsDeleted() == 1) {
            throw new BizException("Agent Trace 日志不存在");
        }

        return AgentTraceLogVO.from(log);
    }
}
