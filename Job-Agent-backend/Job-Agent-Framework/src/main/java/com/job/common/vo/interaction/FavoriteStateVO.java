package com.job.common.vo.interaction;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:岗位收藏状态响应对象，返回给前端控制收藏按钮文案
 * 日期:2026/6/6 16:10
 */
@Data
public class FavoriteStateVO {

    /**
     * 岗位ID。
     */
    private Long positionId;

    /**
     * 当前用户是否已收藏该岗位。
     */
    private Boolean favorited;

    /**
     * 该岗位被收藏的次数。
     */
    private Long favoriteCount;

    /**
     * 快速构造收藏状态响应对象。
     *
     * @param positionId 岗位ID
     * @param favorited 当前用户是否已收藏
     * @param favoriteCount 收藏总数
     * @return 返回前端使用的收藏状态
     */
    public static FavoriteStateVO of(Long positionId, Boolean favorited, Long favoriteCount) {
        FavoriteStateVO response = new FavoriteStateVO();
        response.setPositionId(positionId);
        response.setFavorited(favorited);
        response.setFavoriteCount(favoriteCount);
        return response;
    }
}
