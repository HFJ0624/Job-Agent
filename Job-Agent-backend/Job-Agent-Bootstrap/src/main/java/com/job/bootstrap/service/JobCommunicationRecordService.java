package com.job.bootstrap.service;

import com.job.common.dto.communication.*;
import com.job.common.vo.communication.*;

import java.util.List;

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

    /**
     * 保存 HR 回复并生成 AI 建议回复。
     */
    JobCommunicationRecordVO saveHrReplyAndGenerateReply(Long userId, Long id, HrReplyGenerateDTO dto);

    /**
     * 标记用户已把回复发送给 HR。
     */
    JobCommunicationRecordVO markUserReplySent(Long userId, Long id, UserReplySentDTO dto);

    /**
     * 手动更新沟通状态。
     */
    JobCommunicationRecordVO updateStatus(Long userId, Long id, CommunicationStatusUpdateDTO dto);

    /**
     * 查询某条沟通记录下的消息流水。
     */
    List<JobCommunicationMessageVO> listMessages(Long userId, Long communicationId);
    /**
     * 从 HR 回复中提取面试邀约信息。
     */
    InterviewInviteExtractVO extractInterviewInvite(Long userId, Long id, InterviewInviteExtractDTO dto);

    /**
     * 用户确认并保存面试邀约信息。
     */
    JobCommunicationRecordVO confirmInterviewInvite(Long userId, Long id, InterviewInviteConfirmDTO dto);
}
