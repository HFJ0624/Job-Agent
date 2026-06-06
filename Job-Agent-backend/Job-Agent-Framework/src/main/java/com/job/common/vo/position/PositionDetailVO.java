package com.job.common.vo.position;

import com.job.common.entity.company.JobCompany;
import com.job.common.entity.position.JobPosition;
import com.job.common.vo.company.CompanyVO;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:前台岗位详情响应对象，一次性返回岗位详情、公司详情和当前用户收藏状态
 * 日期:2026/6/6 16:10
 */
@Data
public class PositionDetailVO {

    /**
     * 岗位完整信息。
     */
    private PositionVO position;

    /**
     * 公司完整信息。
     */
    private CompanyVO company;

    /**
     * 当前登录用户是否已收藏。
     * P表示参数描述，未登录访问详情页时固定为 false。
     */
    private Boolean favorited;

    /**
     * 该岗位被收藏的次数。
     */
    private Long favoriteCount;

    /**
     * 将岗位、公司和收藏状态组合成详情页 VO。
     *
     * @param position 岗位实体
     * @param company 公司实体
     * @param favorited 当前用户是否已收藏
     * @param favoriteCount 收藏总数
     * @return 返回岗位详情页数据
     */
    public static PositionDetailVO from(JobPosition position, JobCompany company, Boolean favorited, Long favoriteCount) {
        PositionDetailVO response = new PositionDetailVO();
        response.setPosition(PositionVO.from(position, company));
        response.setCompany(company == null ? null : CompanyVO.from(company));
        response.setFavorited(Boolean.TRUE.equals(favorited));
        response.setFavoriteCount(favoriteCount == null ? 0L : favoriteCount);
        return response;
    }
}
