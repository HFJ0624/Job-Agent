package com.job.bootstrap.service;

import com.job.common.dto.communication.*;
import com.job.common.vo.communication.*;

import java.util.List;

/**
 * 求职沟通记录服务接口。
 *
 * <p>核心职责：记录用户与 HR 的沟通过程，支持手动录入、AI 辅助回复、面试邀约提取及状态流转。</p>
 *
 * <p>所属业务模块：求职管理 - 沟通跟踪</p>
 *
 * <p>主要调用链：
 * JobCommunicationController / JobGreetingService -&gt; JobCommunicationRecordService -&gt; JobCommunicationRecordServiceImpl -&gt; JobCommunicationRecordRepository / AiModelGatewayService / JobReminderService</p>
 */
public interface JobCommunicationRecordService {

    /**
     * 分页查询当前用户的沟通记录。
     *
     * @param userId 当前用户 ID
     * @param queryDTO 查询条件
     * @return 沟通记录分页结果
     */
    JobCommunicationPageVO pageCommunications(Long userId, JobCommunicationQueryDTO queryDTO);

    /**
     * 查询沟通记录详情。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @return 沟通记录详情
     */
    JobCommunicationRecordVO getDetail(Long userId, Long id);

    /**
     * 手动创建沟通记录。
     *
     * @param userId 当前用户 ID
     * @param createDTO 沟通记录创建参数
     * @return 创建后的沟通记录
     */
    JobCommunicationRecordVO create(Long userId, JobCommunicationCreateDTO createDTO);

    /**
     * 生成打招呼语后自动创建沟通记录。
     *
     * @param userId 当前用户 ID
     * @param resumeId 简历 ID
     * @param jobId 岗位 ID
     * @param greetingRecordId 打招呼语记录 ID
     * @param greetingText 打招呼语内容
     * @return 创建后的沟通记录
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
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @return 更新后的沟通记录
     */
    JobCommunicationRecordVO markCopied(Long userId, Long id);

    /**
     * 标记已沟通。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @return 更新后的沟通记录
     */
    JobCommunicationRecordVO markCommunicated(Long userId, Long id);

    /**
     * 保存 HR 回复。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @param replyDTO HR 回复内容
     * @return 更新后的沟通记录
     */
    JobCommunicationRecordVO saveHrReply(Long userId, Long id, JobCommunicationReplyDTO replyDTO);

    /**
     * 标记邀约面试。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @param interviewDTO 面试邀约参数
     * @return 更新后的沟通记录
     */
    JobCommunicationRecordVO markInterviewInvited(Long userId, Long id, JobCommunicationInterviewDTO interviewDTO);

    /**
     * 关闭沟通记录。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @return 更新后的沟通记录
     */
    JobCommunicationRecordVO closeCommunication(Long userId, Long id);

    /**
     * 查询统计数据。
     *
     * @param userId 当前用户 ID
     * @return 沟通统计结果
     */
    JobCommunicationStatsVO getStats(Long userId);

    /**
     * 保存 HR 回复并生成 AI 建议回复。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @param dto HR 回复及生成参数
     * @return 更新后的沟通记录，包含 AI 建议回复
     */
    JobCommunicationRecordVO saveHrReplyAndGenerateReply(Long userId, Long id, HrReplyGenerateDTO dto);

    /**
     * 标记用户已把回复发送给 HR。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @param dto 用户发送回复参数
     * @return 更新后的沟通记录
     */
    JobCommunicationRecordVO markUserReplySent(Long userId, Long id, UserReplySentDTO dto);

    /**
     * 手动更新沟通状态。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @param dto 状态更新参数
     * @return 更新后的沟通记录
     */
    JobCommunicationRecordVO updateStatus(Long userId, Long id, CommunicationStatusUpdateDTO dto);

    /**
     * 查询某条沟通记录下的消息流水。
     *
     * @param userId 当前用户 ID
     * @param communicationId 沟通记录 ID
     * @return 消息流水列表
     */
    List<JobCommunicationMessageVO> listMessages(Long userId, Long communicationId);

    /**
     * 从 HR 回复中提取面试邀约信息。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @param dto 提取参数
     * @return 提取到的面试邀约信息
     */
    InterviewInviteExtractVO extractInterviewInvite(Long userId, Long id, InterviewInviteExtractDTO dto);

    /**
     * 用户确认并保存面试邀约信息。
     *
     * @param userId 当前用户 ID
     * @param id 沟通记录 ID
     * @param dto 面试邀约确认参数
     * @return 更新后的沟通记录
     */
    JobCommunicationRecordVO confirmInterviewInvite(Long userId, Long id, InterviewInviteConfirmDTO dto);
}
