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
 * 作者:hfj
 * 功能:前台岗位互动服务实现，处理岗位收藏和立即沟通消息保存
 * 日期:2026/6/6 16:10
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
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @return true 表示已收藏
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
     * P表示参数描述，使用逻辑删除恢复方式处理重复收藏，避免同一个用户同一个岗位插入多条有效收藏记录。
     *
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @return 返回最新收藏状态
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
     * @param userId 当前登录用户ID
     * @param positionId 岗位ID
     * @param content 用户输入的消息内容
     * @return 返回消息实体
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
     * 查询已发布岗位，不存在或未发布时抛出业务异常。
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
     * 查询公司信息，允许为空，避免公司历史数据缺失时影响消息保存。
     *
     * @param companyId 公司ID
     * @return 返回公司实体
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
