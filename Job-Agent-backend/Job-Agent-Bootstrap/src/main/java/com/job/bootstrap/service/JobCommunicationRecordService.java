package com.job.bootstrap.service;

import com.job.common.dto.communication.JobCommunicationCreateDTO;
import com.job.common.dto.communication.JobCommunicationInterviewDTO;
import com.job.common.dto.communication.JobCommunicationQueryDTO;
import com.job.common.dto.communication.JobCommunicationReplyDTO;
import com.job.common.vo.communication.JobCommunicationPageVO;
import com.job.common.vo.communication.JobCommunicationRecordVO;
import com.job.common.vo.communication.JobCommunicationStatsVO;

/**
 * 作者: hfj
 * 功能: 求职沟通记录服务
 */
public interface JobCommunicationRecordService {

    /**
     * 分页查询当前用户的沟通记录。
     */
    JobCommunicationPageVO pageCommunications(Long userId, JobCommunicationQueryDTO queryDTO);

    /**
     * 查询沟通记录详情。
     */
    JobCommunicationRecordVO getDetail(Long userId, Long id);

    /**
     * 手动创建沟通记录。
     */
    JobCommunicationRecordVO create(Long userId, JobCommunicationCreateDTO createDTO);

    /**
     * 生成打招呼语后自动创建沟通记录。
     */
    JobCommunicationRecordVO createFromGreeting(
            Long userId,
            Long resumeId,
            Long jobId,
            Long greetingRecordId,
            String greetingText
    );

    /**
     * 标记已复制话术。
     */
    JobCommunicationRecordVO markCopied(Long userId, Long id);

    /**
     * 标记已沟通。
     */
    JobCommunicationRecordVO markCommunicated(Long userId, Long id);

    /**
     * 保存 HR 回复。
     */
    JobCommunicationRecordVO saveHrReply(Long userId, Long id, JobCommunicationReplyDTO replyDTO);

    /**
     * 标记邀约面试。
     */
    JobCommunicationRecordVO markInterviewInvited(Long userId, Long id, JobCommunicationInterviewDTO interviewDTO);

    /**
     * 关闭沟通记录。
     */
    JobCommunicationRecordVO closeCommunication(Long userId, Long id);

    /**
     * 查询统计数据。
     */
    JobCommunicationStatsVO getStats(Long userId);
}
