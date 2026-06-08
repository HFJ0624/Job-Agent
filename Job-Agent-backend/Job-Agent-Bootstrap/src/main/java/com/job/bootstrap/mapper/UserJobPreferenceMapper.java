package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.preference.UserJobPreference;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:用户求职偏好 Mapper
 */
@Mapper
public interface UserJobPreferenceMapper extends BaseMapper<UserJobPreference> {
}
