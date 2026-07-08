package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.JobCompanyMapper;
import com.job.bootstrap.mapper.JobPositionFavoriteMapper;
import com.job.bootstrap.mapper.JobPositionMapper;
import com.job.bootstrap.mapper.JobPositionMessageMapper;
import com.job.bootstrap.service.JobInteractionService;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.interaction.JobPositionFavorite;
import com.job.common.entity.interaction.JobPositionMessage;
import com.job.common.entity.position.JobPosition;
import com.job.common.vo.interaction.FavoriteStateVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 前台岗位互动服务实现类。
 *
 * <p>核心职责：负责求职用户与岗位之间的互动行为，包括岗位收藏（Favorite）状态的查询、切换
 * 与统计，以及“立即沟通”消息（Message）的保存。提供岗位发布状态校验与公司信息兜底能力。</p>
 *
 * <p>所属业务模块：岗位互动模块（Job Interaction）</p>
 *
 * <p>主要调用链：
 * <pre>
 * JobInteractionController -&gt; JobInteractionService -&gt; JobInteractionServiceImpl
 *                                       |
 *                                       v
 *              JobPositionMapper / JobCompanyMapper / JobPositionFavoriteMapper / JobPositionMessageMapper
 * </pre></p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>直接操作 {@link JobPositionFavoriteMapper} 与 {@link JobPositionMessageMapper} 进行互动数据持久化</li>
 *   <li>依赖 {@link JobPositionMapper} 校验岗位存在性与发布状态</li>
 *   <li>依赖 {@link JobCompanyMapper} 查询公司信息，用于生成沟通默认消息</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>收藏使用逻辑删除恢复机制，避免同一用户同一岗位产生多条有效收藏记录</li>
 *   <li>所有写操作均使用 {@link Transactional} 保证事务一致性</li>
 *   <li>沟通消息在用户未填写内容时自动生成默认话术，提升用户体验</li>
 *   <li>收藏与沟通前均强制校验岗位是否已发布，防止对草稿岗位产生互动</li>
 * </ul></p>
 *
 * @author hfj
 * @since 2026/6/6
 */
@Service
@RequiredArgsConstructor
public class JobInteractionServiceImpl implements JobInteractionService {

    /**
     * 岗位已发布状态。
     */
    private static final int STATUS_PUBLISHED = 1;

    /**
     * 逻辑未删除。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 逻辑已删除。
     */
    private static final int DELETED = 1;

    /**
     * 求职用户发送消息。
     */
    private static final String SENDER_USER = "USER";

    /**
     * 消息已发送状态。
     */
    private static final String MESSAGE_STATUS_SENT = "SENT";

    private final JobPositionMapper jobPositionMapper;

    private final JobCompanyMapper jobCompanyMapper;

    private final JobPositionFavoriteMapper favoriteMapper;

    private final JobPositionMessageMapper messageMapper;

    /**
     * 判断用户是否收藏指定岗位。
     *
     * <p>查询用户与岗位之间是否存在未删除的收藏记录；userId 或 positionId 为空时直接返回 false。</p>
     *
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @return true 表示已收藏，false 表示未收藏或参数为空
     */
    @Override
    public boolean isFavorited(Long userId, Long positionId) {
        if (userId == null || positionId == null) {
            return false;
        }
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<JobPositionFavorite>()
                .eq(JobPositionFavorite::getUserId, userId)
                .eq(JobPositionFavorite::getPositionId, positionId)
                .eq(JobPositionFavorite::getIsDeleted, NOT_DELETED));
        return count != null && count > 0;
    }

    /**
     * 统计岗位收藏数。
     *
     * <p>统计指定岗位下未删除的收藏记录数；positionId 为空时返回 0。</p>
     *
     * @param positionId 岗位ID
     * @return 返回未删除收藏数
     */
    @Override
    public Long countFavorites(Long positionId) {
        if (positionId == null) {
            return 0L;
        }
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<JobPositionFavorite>()
                .eq(JobPositionFavorite::getPositionId, positionId)
                .eq(JobPositionFavorite::getIsDeleted, NOT_DELETED));
        return count == null ? 0L : count;
    }

    /**
     * 切换收藏状态。
     *
     * <p>对指定岗位执行收藏/取消收藏操作：未收藏时新增记录，已收藏时逻辑删除，
     * 已取消时恢复记录。使用逻辑删除恢复机制避免同一用户同一岗位产生多条有效收藏记录。</p>
     *
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @return 返回最新收藏状态（包含收藏标记与当前收藏总数）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteStateVO toggleFavorite(Long userId, Long positionId) {
        JobPosition position = getPublishedPositionRequired(positionId);
        Date now = new Date();

        JobPositionFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<JobPositionFavorite>()
                .eq(JobPositionFavorite::getUserId, userId)
                .eq(JobPositionFavorite::getPositionId, positionId)
                .last("limit 1"));

        boolean favorited;
        if (favorite == null) {
            favorite = new JobPositionFavorite();
            favorite.setUserId(userId);
            favorite.setPositionId(positionId);
            favorite.setCompanyId(position.getCompanyId());
            favorite.setIsDeleted(NOT_DELETED);
            favorite.setCreateTime(now);
            favorite.setUpdateTime(now);
            favoriteMapper.insert(favorite);
            favorited = true;
        } else if (favorite.getIsDeleted() != null && favorite.getIsDeleted() == NOT_DELETED) {
            favorite.setIsDeleted(DELETED);
            favorite.setUpdateTime(now);
            favoriteMapper.updateById(favorite);
            favorited = false;
        } else {
            favorite.setCompanyId(position.getCompanyId());
            favorite.setIsDeleted(NOT_DELETED);
            favorite.setUpdateTime(now);
            favoriteMapper.updateById(favorite);
            favorited = true;
        }

        return FavoriteStateVO.of(positionId, favorited, countFavorites(positionId));
    }

    /**
     * 保存立即沟通消息。
     *
     * <p>校验岗位已发布后，保存用户沟通消息；若用户未输入内容，则自动生成包含公司与岗位名称的默认话术。</p>
     *
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @param content 用户输入的消息内容
     * @return 返回保存后的消息实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobPositionMessage communicate(Long userId, Long positionId, String content) {
        JobPosition position = getPublishedPositionRequired(positionId);
        JobCompany company = getCompany(position.getCompanyId());
        Date now = new Date();

        JobPositionMessage message = new JobPositionMessage();
        message.setUserId(userId);
        message.setPositionId(positionId);
        message.setCompanyId(position.getCompanyId());
        message.setSenderType(SENDER_USER);
        message.setContent(defaultContent(content, position, company));
        message.setStatus(MESSAGE_STATUS_SENT);
        message.setIsDeleted(NOT_DELETED);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        messageMapper.insert(message);
        return message;
    }

    /**
     * 查询已发布岗位。
     *
     * <p>校验岗位存在且状态为已发布；岗位不存在或处于草稿状态时抛出 {@link BizException}，
     * 用于收藏与沟通前的前置校验。</p>
     *
     * @param positionId 岗位ID
     * @return 返回岗位实体
     */
    private JobPosition getPublishedPositionRequired(Long positionId) {
        JobPosition position = jobPositionMapper.selectOne(new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getId, positionId)
                .eq(JobPosition::getIsDeleted, NOT_DELETED)
                .last("limit 1"));
        if (position == null) {
            throw new BizException("岗位不存在");
        }
        if (position.getStatus() == null || position.getStatus() != STATUS_PUBLISHED) {
            throw new BizException("岗位暂未发布，不能进行收藏或沟通");
        }
        return position;
    }

    /**
     * 查询公司信息。
     *
     * <p>允许返回 null，避免公司历史数据缺失时影响消息保存；用于生成沟通默认话术时兜底。</p>
     *
     * @param companyId 公司ID
     * @return 返回公司实体，可能为 null
     */
    private JobCompany getCompany(Long companyId) {
        if (companyId == null) {
            return null;
        }
        return jobCompanyMapper.selectById(companyId);
    }

    /**
     * 生成立即沟通默认消息。
     *
     * <p>用户未输入内容时，自动生成包含公司名称与岗位名称的标准沟通话术，提升用户体验。</p>
     *
     * @param content 用户输入内容
     * @param position 岗位实体
     * @param company 公司实体
     * @return 返回最终保存的消息内容
     */
    private String defaultContent(String content, JobPosition position, JobCompany company) {
        if (StringUtils.hasText(content)) {
            return content.trim();
        }
        String companyName = company == null ? "贵公司" : company.getCompanyName();
        return "您好，我对「" + companyName + " - " + position.getJobTitle() + "」岗位很感兴趣，想进一步沟通一下。";
    }
}
