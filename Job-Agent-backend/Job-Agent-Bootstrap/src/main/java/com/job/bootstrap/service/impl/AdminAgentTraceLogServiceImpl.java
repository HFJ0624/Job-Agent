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
 * 后台 Agent Trace 日志服务实现。
 *
 * <p>核心职责：为后台运营人员提供 Agent Trace 日志的分页查询与详情查看能力，
 * 支持按 Trace ID、用户 ID、会话 ID、意图编码、工具名、状态及时间范围等多维度检索，
 * 帮助管理员排查 Agent 单次调用的完整执行轨迹。</p>
 *
 * <p>所属业务模块：Agent 运营中心 - Trace 日志</p>
 *
 * <p>主要调用链：
 * AdminAgentTraceLogController → {@link AdminAgentTraceLogServiceImpl} →
 * AgentTraceLogMapper → 返回 AgentTraceLogVO 分页或详情</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link AgentTraceLogMapper} 读取 Agent Trace 日志持久化数据</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>本服务只提供查询能力，不暴露修改接口，保证 Trace 日志的不可篡改性。</li>
 *   <li>分页参数做防御性处理，限制最大 pageSize 为 100，防止大数据量查询拖垮后台。</li>
 *   <li>新日志按创建时间倒序排列，方便管理员优先查看最近的问题。</li>
 * </ul></p>
 *
 * @author hfj
 * @since 2026/6/8
 */
@Service
@RequiredArgsConstructor
public class AdminAgentTraceLogServiceImpl implements AdminAgentTraceLogService {

    private static final int NOT_DELETED = 0;

    private final AgentTraceLogMapper agentTraceLogMapper;

    /**
     * 分页查询 Agent Trace 日志。
     *
     * <p>方法步骤：</p>
     * <ol>
     *   <li>防御性处理分页参数，空值或负数时回退到默认值，并限制最大 pageSize 为 100。</li>
     *   <li>动态拼接查询条件，支持 Trace ID、用户 ID、会话 ID、意图编码、工具名、状态及时间范围。</li>
     *   <li>按创建时间倒序执行分页查询，保证最新日志优先展示。</li>
     *   <li>将 Entity 分页结果转换为 VO 分页返回。</li>
     * </ol>
     *
     * @param query Trace 日志查询条件，包含分页及多维度过滤参数
     * @return Agent Trace 日志 VO 分页结果
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
     * 查询 Agent Trace 日志详情。
     *
     * @param id Trace 日志主键 ID
     * @return Trace 日志详情 VO
     * @throws BizException 当日志不存在或已删除时抛出
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
