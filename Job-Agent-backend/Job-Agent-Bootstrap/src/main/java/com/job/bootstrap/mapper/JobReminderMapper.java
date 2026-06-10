package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.common.dto.reminder.ReminderQueryDTO;
import com.job.common.entity.reminder.JobReminder;
import com.job.common.vo.reminder.JobReminderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 作者: hfj
 * 功能: 求职提醒 Mapper
 */
@Mapper
public interface JobReminderMapper extends BaseMapper<JobReminder> {

    /**
     * 多表分页查询提醒列表。
     *
     * 关联岗位、公司、简历，是为了前端展示时不要只显示 jobId、resumeId。
     */
    Page<JobReminderVO> selectReminderPage(
            Page<JobReminderVO> page,
            @Param("userId") Long userId,
            @Param("query") ReminderQueryDTO query
    );
}
