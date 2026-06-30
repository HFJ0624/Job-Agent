package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentDailyReportVO;
import com.job.common.dto.agent.AgentDailyReportSubscriptionSaveDTO;
import com.job.common.vo.agent.AgentDailyReportSubscriptionVO;

import java.util.Date;
import java.util.List;

/**
 * Agent 主动日报服务。
 */
public interface AgentDailyReportService {

    /**
     * 为指定用户生成某一天的日报。
     *
     * @param userId 用户 ID
     * @param reportDate 日报日期
     * @param sendEmail 是否尝试发送邮件
     * @return 生成后的日报
     */
    AgentDailyReportVO generateForUser(Long userId, Date reportDate, boolean sendEmail);

    /**
     * 查询用户最近的日报。
     *
     * @param userId 用户 ID
     * @param limit 查询条数
     * @return 最近日报列表
     */
    List<AgentDailyReportVO> listRecent(Long userId, int limit);

    /**
     * 查询用户日报订阅配置。没有配置时返回默认配置。
     *
     * @param userId 用户 ID
     * @return 订阅配置
     */
    AgentDailyReportSubscriptionVO getSubscription(Long userId);

    /**
     * 保存用户日报订阅配置。
     *
     * @param userId 用户 ID
     * @param dto 保存参数
     * @return 保存后的订阅配置
     */
    AgentDailyReportSubscriptionVO saveSubscription(Long userId, AgentDailyReportSubscriptionSaveDTO dto);

    /**
     * 为当前时间命中的订阅用户生成今日日报。
     *
     * @return 本次成功生成或更新的日报数量
     */
    int generateDueSubscriptions();
}
