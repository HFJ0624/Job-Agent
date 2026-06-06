package com.job.common.entity.interaction;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:岗位收藏实体类，对应数据库 job_position_favorite 表
 * 日期:2026/6/6 16:10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_position_favorite")
public class JobPositionFavorite extends BaseEntity {

    /**
     * 用户ID。
     * P表示参数描述，收藏行为必须绑定当前登录用户，不能让前端传 userId。
     */
    private Long userId;

    /**
     * 岗位ID。
     */
    private Long positionId;

    /**
     * 公司ID。
     * P表示参数描述，冗余公司ID是为了后续做“我收藏的公司/岗位”统计时少一次关联查询。
     */
    private Long companyId;
}
