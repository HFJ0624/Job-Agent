package com.job.bootstrap.service;

import com.job.common.entity.interaction.JobPositionMessage;
import com.job.common.vo.interaction.FavoriteStateVO;

/**
 * 前台岗位互动服务接口。
 *
 * <p>核心职责：为用户提供岗位收藏、取消收藏及立即沟通等互动能力，维护用户与岗位的关联关系。</p>
 *
 * <p>所属业务模块：用户端 - 岗位互动</p>
 *
 * <p>主要调用链：
 * JobInteractionController -&gt; JobInteractionService -&gt; JobInteractionServiceImpl -&gt; JobPositionRepository / JobCommunicationRecordService / JobGreetingService</p>
 */
public interface JobInteractionService {

    /**
     * 判断当前用户是否收藏过该岗位。
     *
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @return true 表示已收藏
     */
    boolean isFavorited(Long userId, Long positionId);

    /**
     * 统计岗位收藏数量。
     *
     * @param positionId 岗位ID
     * @return 返回未删除收藏记录数量
     */
    Long countFavorites(Long positionId);

    /**
     * 切换收藏状态。
     * P表示参数描述，未收藏时点击会收藏，已收藏时点击会取消收藏。
     *
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @return 返回最新收藏状态
     */
    FavoriteStateVO toggleFavorite(Long userId, Long positionId);

    /**
     * 发送立即沟通消息给岗位所属公司的 HR。
     *
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @param content 用户输入的消息内容，为空时使用默认招呼语
     * @return 返回已保存的消息实体
     */
    JobPositionMessage communicate(Long userId, Long positionId, String content);
}
