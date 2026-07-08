package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentDailyReportVO;
import com.job.common.dto.agent.AgentDailyReportSubscriptionSaveDTO;
import com.job.common.vo.agent.AgentDailyReportSubscriptionVO;

import java.util.Date;
import java.util.List;

/**
 * Agent 主动日报服务。
 *
 * <p>核心职责：为求职 Agent 用户提供每日求职进展主动汇报能力，支持日报生成、历史查询、邮件投递及订阅配置管理。</p>
 *
 * <p>所属业务模块：Agent 日报 / 用户端主动服务</p>
 *
 * <p>主要调用链：Front Controller / 定时任务 → AgentDailyReportService → 日报领域 Service / Mapper / 邮件发送服务</p>
 */
public interface AgentDailyReportService {

    /**
     * 为指定用户生成指定日期的日报。
     *
     * @param userId     用户 ID
     * @param reportDate 日报日期
     * @param sendEmail  是否尝试发送邮件投递
     * @return 生成后的日报详情，包含求职进展统计、关键事项、下一步建议
     */
    AgentDailyReportVO generateForUser(Long userId, Date reportDate, boolean sendEmail);

    /**
     * 查询用户最近的日报列表。
     *
     * @param userId 用户 ID
     * @param limit  查询条数上限
     * @return 最近日报列表，按日期倒序排列
     */
    List<AgentDailyReportVO> listRecent(Long userId, int limit);

    /**
     * 查询用户日报订阅配置。无配置时返回系统默认配置。
     *
     * @param userId 用户 ID
     * @return 日报订阅配置，包含投递方式、投递时间、内容偏好等
     */
    AgentDailyReportSubscriptionVO getSubscription(Long userId);

    /**
     * 保存用户日报订阅配置。
     *
     * @param userId 用户 ID
     * @param dto    订阅配置保存参数（投递方式、时间、开关等）
     * @return 保存后的订阅配置
     */
    AgentDailyReportSubscriptionVO saveSubscription(Long userId, AgentDailyReportSubscriptionSaveDTO dto);

    /**
     * 为当前时间点命中的订阅用户批量生成今日日报。
     * <p>通常由定时任务触发，按用户配置的投递时间窗口执行。</p>
     *
     * @return 本次成功生成或更新的日报数量
     */
    int generateDueSubscriptions();
}
