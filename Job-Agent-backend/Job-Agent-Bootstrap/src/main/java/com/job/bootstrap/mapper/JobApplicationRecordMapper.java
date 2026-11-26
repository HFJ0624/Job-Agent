package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.application.JobApplicationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:求职投递记录 Mapper
 */
@Mapper
public interface JobApplicationRecordMapper extends BaseMapper<JobApplicationRecord> {

    /**
     * 抢占“面试通知邮件发送权”。
     *
     * 通过把 interview_email_sent_at 从 NULL 原子置为当前时间实现幂等：
     * 只有更新成功（返回 1）的执行才拿到发送权；重复执行/重试会返回 0 从而跳过发送。
     *
     * @param id     求职记录 ID（工作流任务 bizId）
     * @param sentAt 抢占时间
     * @return 影响行数，1 = 抢占成功，0 = 已被抢占（已发送/已标记）
     */
    @Update("UPDATE job_application_record SET interview_email_sent_at = #{sentAt}, update_time = #{sentAt} "
            + "WHERE id = #{id} AND interview_email_sent_at IS NULL AND is_deleted = 0")
    int claimInterviewEmailNotify(@Param("id") Long id, @Param("sentAt") Date sentAt);

    /**
     * 回滚“面试通知邮件发送权”。
     *
     * 邮件发送失败时调用，把 interview_email_sent_at 重置为 NULL，
     * 让工作流下次重试可以重新发送，避免漏发。
     *
     * @param id  求职记录 ID
     * @param now 回滚时间
     * @return 影响行数
     */
    @Update("UPDATE job_application_record SET interview_email_sent_at = NULL, update_time = #{now} "
            + "WHERE id = #{id} AND is_deleted = 0")
    int clearInterviewEmailNotify(@Param("id") Long id, @Param("now") Date now);
}
