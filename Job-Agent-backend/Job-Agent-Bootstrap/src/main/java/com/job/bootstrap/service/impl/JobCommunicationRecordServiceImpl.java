package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.agent.HrCommunicationAssistant;
import com.job.bootstrap.mapper.JobCommunicationMessageMapper;
import com.job.bootstrap.mapper.JobCommunicationRecordMapper;
import com.job.bootstrap.service.JobCommunicationRecordService;
import com.job.common.dto.communication.*;
import com.job.common.entity.communication.JobCommunicationMessage;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.common.vo.communication.JobCommunicationMessageVO;
import com.job.common.vo.communication.JobCommunicationPageVO;
import com.job.common.vo.communication.JobCommunicationRecordVO;
import com.job.common.vo.communication.JobCommunicationStatsVO;
import com.job.enums.CommunicationMessageType;
import com.job.enums.CommunicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 作者: hfj
 * 功能: 求职沟通记录服务实现
 *
 * 设计说明:
 * 1. 这不是自动投递模块。
 * 2. 这是用户在外部平台和 HR 沟通后的过程记录。
 * 3. 生成打招呼语后，系统自动创建沟通记录。
 * 4. 用户复制、沟通、收到回复、邀约面试，都在这里流转状态。
 */
@Service
@RequiredArgsConstructor
public class JobCommunicationRecordServiceImpl implements JobCommunicationRecordService {

    private static final int NOT_DELETED = 0;

    private final JobCommunicationRecordMapper jobCommunicationRecordMapper;

    private final JobCommunicationMessageMapper jobCommunicationMessageMapper;

    private final HrCommunicationAssistant hrCommunicationAssistant;

    /**
     * 分页查询沟通记录。
     */
    @Override
    public JobCommunicationPageVO pageCommunications(Long userId, JobCommunicationQueryDTO queryDTO) {
        Long pageNo = queryDTO.getPageNo() == null ? 1L : queryDTO.getPageNo();
        Long pageSize = queryDTO.getPageSize() == null ? 10L : queryDTO.getPageSize();

        /*
         * 使用自定义多表关联查询。
         *
         * 目的:
         * 1. 不再只返回 resumeId、jobId。
         * 2. 直接返回 resumeName、jobTitle、companyName。
         * 3. 前端页面可以直接展示用户可读信息。
         */
        Page<JobCommunicationRecordVO> page = jobCommunicationRecordMapper.selectCommunicationPage(
                new Page<>(pageNo, pageSize),
                userId,
                queryDTO
        );

        /*
         * 给每条记录补充状态中文和薪资展示文本。
         * 这些字段不一定适合直接在 SQL 里拼，放在 Java 里更清晰。
         */
        page.getRecords().forEach(this::fillDisplayFields);

        JobCommunicationPageVO vo = new JobCommunicationPageVO();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);

        return vo;
    }

    @Override
    public List<JobCommunicationMessageVO> listMessages(Long userId, Long communicationId) {
        /*
         * 先校验主记录归属，防止越权查看别人沟通消息。
         */
        getUserRecordRequired(userId, communicationId);

        List<JobCommunicationMessage> messages = jobCommunicationMessageMapper.selectList(
                new LambdaQueryWrapper<JobCommunicationMessage>()
                        .eq(JobCommunicationMessage::getUserId, userId)
                        .eq(JobCommunicationMessage::getCommunicationId, communicationId)
                        .eq(JobCommunicationMessage::getIsDeleted, NOT_DELETED)
                        .orderByAsc(JobCommunicationMessage::getId)
        );

        return messages.stream().map(this::toMessageVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCommunicationRecordVO updateStatus(
            Long userId,
            Long id,
            CommunicationStatusUpdateDTO dto
    ) {
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        if (!StringUtils.hasText(dto.getCommunicationStatus())) {
            throw new IllegalArgumentException("沟通状态不能为空");
        }

        /*
         * 校验状态是否合法。
         */
        CommunicationStatus.valueOf(dto.getCommunicationStatus());

        record.setCommunicationStatus(dto.getCommunicationStatus());
        record.setInterviewTime(dto.getInterviewTime());
        record.setNextFollowTime(dto.getNextFollowTime());
        record.setNote(dto.getNote());

        jobCommunicationRecordMapper.updateById(record);

        /*
         * 状态流转也保存一条消息流水，方便前端时间线展示。
         */
        saveMessage(
                userId,
                id,
                "STATUS_CHANGE",
                "状态更新为：" + dto.getCommunicationStatus(),
                null,
                dto.getCommunicationStatus()
        );

        return getDetail(userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCommunicationRecordVO markUserReplySent(
            Long userId,
            Long id,
            UserReplySentDTO dto
    ) {
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        /*
         * 如果用户没有传最终发送内容，就默认使用 AI 生成的回复。
         */
        String finalReply = StringUtils.hasText(dto.getUserReplyText())
                ? dto.getUserReplyText()
                : record.getAiReplyText();

        if (!StringUtils.hasText(finalReply)) {
            throw new IllegalArgumentException("没有可发送给HR的回复内容");
        }

        /*
         * 保存用户发送给 HR 的消息流水。
         */
        saveMessage(
                userId,
                id,
                CommunicationMessageType.USER_TO_HR.name(),
                finalReply,
                null,
                CommunicationStatus.USER_REPLIED.name()
        );

        record.setUserReplyText(finalReply);
        record.setCommunicationStatus(CommunicationStatus.USER_REPLIED.name());

        jobCommunicationRecordMapper.updateById(record);

        return getDetail(userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCommunicationRecordVO saveHrReplyAndGenerateReply(
            Long userId,
            Long id,
            HrReplyGenerateDTO dto
    ) {
        /*
         * 1. 查询并校验沟通记录是否属于当前用户。
         */
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        if (!StringUtils.hasText(dto.getHrReply())) {
            throw new IllegalArgumentException("HR回复内容不能为空");
        }

        /*
         * 2. 保存 HR 回复消息流水。
         *    这样以后可以看到每一轮 HR 说了什么。
         */
        saveMessage(
                userId,
                id,
                CommunicationMessageType.HR_TO_USER.name(),
                dto.getHrReply(),
                null,
                CommunicationStatus.REPLIED.name()
        );

        /*
         * 3. 更新主记录中的最新 HR 回复。
         */
        record.setHrReply(dto.getHrReply());

        /*
         * 4. 用户可以手动选择当前状态。
         *    如果没选，就默认 HR 已回复。
         */
        String selectedStatus = StringUtils.hasText(dto.getProgressStatus())
                ? dto.getProgressStatus()
                : CommunicationStatus.REPLIED.name();

        /*
         * 5. 生成 AI 回复。
         */
        String aiReply = generateAiReply(record, dto);

        /*
         * 6. 保存 AI 回复消息流水。
         */
        saveMessage(
                userId,
                id,
                CommunicationMessageType.AI_SUGGESTION.name(),
                aiReply,
                dto.getReplyStyle(),
                CommunicationStatus.AI_REPLY_GENERATED.name()
        );

        /*
         * 7. 更新主记录。
         *
         * 说明:
         * 如果用户选择了 INTERVIEW_INVITED，说明 HR 回复中已经约面试。
         * 这时状态保留为 INTERVIEW_INVITED。
         *
         * 否则默认变成 AI_REPLY_GENERATED，表示系统已经生成建议回复。
         */
        record.setAiReplyText(aiReply);
        record.setNote(dto.getNote());

        if (CommunicationStatus.INTERVIEW_INVITED.name().equals(selectedStatus)) {
            record.setCommunicationStatus(CommunicationStatus.INTERVIEW_INVITED.name());
        } else if (CommunicationStatus.CLOSED.name().equals(selectedStatus)) {
            record.setCommunicationStatus(CommunicationStatus.CLOSED.name());
        } else {
            record.setCommunicationStatus(CommunicationStatus.AI_REPLY_GENERATED.name());
        }

        jobCommunicationRecordMapper.updateById(record);

        return getDetail(userId, id);
    }

    /**
     * 根据沟通记录和 HR 回复生成 AI 建议回复。
     *
     * @param record 沟通主记录
     * @param dto HR 回复生成 DTO
     * @return AI 回复正文
     */
    private String generateAiReply(JobCommunicationRecord record, HrReplyGenerateDTO dto) {
        /*
         * 先查询带岗位名称、公司名称、简历名称的详情。
         * 这样 prompt 里可以提供更完整上下文。
         */
        JobCommunicationRecordVO detail = jobCommunicationRecordMapper.selectCommunicationDetail(
                record.getUserId(),
                record.getId()
        );

        fillDisplayFields(detail);

        String replyStyle = StringUtils.hasText(dto.getReplyStyle())
                ? dto.getReplyStyle()
                : "自然礼貌";

        String userRequirement = StringUtils.hasText(dto.getUserRequirement())
                ? dto.getUserRequirement()
                : "无额外要求";

        /*
         * 组装 Prompt。
         *
         * 这里不要把所有简历原文都塞进去，第一版先用简历名称、岗位、公司、
         * 打招呼语、HR 回复即可。
         *
         * 后续你做 RAG 后，可以召回简历项目经历和岗位 JD 关键要求。
         */
        String prompt = """
            请根据以下求职沟通上下文，生成一段适合回复 HR 的中文消息。
            
            【公司】
            %s
            
            【岗位】
            %s
            
            【城市】
            %s
            
            【薪资】
            %s
            
            【使用简历】
            %s
            
            【之前发给 HR 的打招呼语】
            %s
            
            【HR 最新回复】
            %s
            
            【回复风格】
            %s
            
            【用户额外要求】
            %s
            
            请直接输出要发给 HR 的回复正文，控制在 80-150 字之间。
            """.formatted(
                nullToDefault(detail.getCompanyName(), "未知公司"),
                nullToDefault(detail.getJobTitle(), "未知岗位"),
                nullToDefault(detail.getJobCity(), "未知城市"),
                nullToDefault(detail.getSalaryText(), "薪资面议"),
                nullToDefault(detail.getResumeName(), "未关联简历"),
                nullToDefault(record.getGreetingText(), "无"),
                dto.getHrReply(),
                replyStyle,
                userRequirement
        );

        return hrCommunicationAssistant.generateReply(prompt);
    }

    /**
     * 空值兜底。
     */
    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 填充前端展示字段。
     *
     * @param vo 沟通记录 VO
     */
    private void fillDisplayFields(JobCommunicationRecordVO vo) {
        /*
         * 1. 填充状态中文。
         */
        vo.setCommunicationStatusDesc(getStatusDesc(vo.getCommunicationStatus()));

        /*
         * 2. 填充薪资文本。
         * 例如 minSalary=15, maxSalary=30，则展示 15-30K。
         */
        if (vo.getMinSalary() != null && vo.getMaxSalary() != null) {
            vo.setSalaryText(vo.getMinSalary() + "-" + vo.getMaxSalary() + "K");
        } else if (vo.getMinSalary() != null) {
            vo.setSalaryText(vo.getMinSalary() + "K以上");
        } else if (vo.getMaxSalary() != null) {
            vo.setSalaryText(vo.getMaxSalary() + "K以内");
        } else {
            vo.setSalaryText("薪资面议");
        }

        /*
         * 3. 容错展示。
         * 如果关联岗位被删除，至少给前端一个兜底文案。
         */
        if (vo.getJobTitle() == null || vo.getJobTitle().isBlank()) {
            vo.setJobTitle("岗位信息已失效");
        }

        if (vo.getResumeName() == null || vo.getResumeName().isBlank()) {
            vo.setResumeName("未关联简历");
        }

        if (vo.getCompanyName() == null || vo.getCompanyName().isBlank()) {
            vo.setCompanyName("未知公司");
        }
    }

    /**
     * 查询详情。
     */
    @Override
    public JobCommunicationRecordVO getDetail(Long userId, Long id) {
        /*
         * 详情页也使用多表关联查询。
         */
        JobCommunicationRecordVO vo = jobCommunicationRecordMapper.selectCommunicationDetail(userId, id);

        if (vo == null) {
            throw new IllegalArgumentException("沟通记录不存在");
        }

        fillDisplayFields(vo);

        return vo;
    }

    /**
     * 手动创建沟通记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCommunicationRecordVO create(Long userId, JobCommunicationCreateDTO createDTO) {
        if (createDTO.getJobId() == null) {
            throw new IllegalArgumentException("岗位ID不能为空");
        }

        JobCommunicationRecord record = new JobCommunicationRecord();

        record.setUserId(userId);
        record.setApplicationId(createDTO.getApplicationId());
        record.setResumeId(createDTO.getResumeId());
        record.setJobId(createDTO.getJobId());
        record.setGreetingRecordId(createDTO.getGreetingRecordId());

        record.setPlatform(StringUtils.hasText(createDTO.getPlatform()) ? createDTO.getPlatform() : "BOSS");
        record.setExternalJobUrl(createDTO.getExternalJobUrl());
        record.setHrName(createDTO.getHrName());
        record.setHrContact(createDTO.getHrContact());
        record.setGreetingText(createDTO.getGreetingText());
        record.setNote(createDTO.getNote());

        /*
         * 新建时默认是已生成话术。
         */
        record.setCommunicationStatus(CommunicationStatus.GREETING_GENERATED.name());

        record.setIsDeleted(NOT_DELETED);

        jobCommunicationRecordMapper.insert(record);

        return getDetail(userId, record.getId());
    }

    /**
     * 生成打招呼语后自动创建沟通记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCommunicationRecordVO createFromGreeting(
            Long userId,
            Long resumeId,
            Long jobId,
            Long greetingRecordId,
            String greetingText
    ) {
        JobCommunicationCreateDTO dto = new JobCommunicationCreateDTO();
        dto.setResumeId(resumeId);
        dto.setJobId(jobId);
        dto.setGreetingRecordId(greetingRecordId);
        dto.setGreetingText(greetingText);
        dto.setPlatform("BOSS");
        dto.setNote("由 HR 打招呼语生成后自动创建");

        return create(userId, dto);
    }

    /**
     * 标记已复制。
     */
    @Override
    public JobCommunicationRecordVO markCopied(Long userId, Long id) {
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        record.setCommunicationStatus(CommunicationStatus.COPIED.name());
        jobCommunicationRecordMapper.updateById(record);

        /*
         * 更新后重新走详情关联查询，保证返回 resumeName、jobTitle、companyName。
         */
        return getDetail(userId, id);
    }

    /**
     * 标记已沟通。
     */
    @Override
    public JobCommunicationRecordVO markCommunicated(Long userId, Long id) {
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        record.setCommunicationStatus(CommunicationStatus.COMMUNICATED.name());
        jobCommunicationRecordMapper.updateById(record);

        /*
         * 更新后重新走详情关联查询，保证返回 resumeName、jobTitle、companyName。
         */
        return getDetail(userId, id);
    }

    /**
     * 保存 HR 回复。
     */
    @Override
    public JobCommunicationRecordVO saveHrReply(Long userId, Long id, JobCommunicationReplyDTO replyDTO) {
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        if (!StringUtils.hasText(replyDTO.getHrReply())) {
            throw new IllegalArgumentException("HR回复内容不能为空");
        }

        record.setHrReply(replyDTO.getHrReply());
        record.setNote(replyDTO.getNote());
        record.setCommunicationStatus(CommunicationStatus.REPLIED.name());

        jobCommunicationRecordMapper.updateById(record);

        return getDetail(userId, id);
    }

    /**
     * 标记邀约面试。
     */
    @Override
    public JobCommunicationRecordVO markInterviewInvited(
            Long userId,
            Long id,
            JobCommunicationInterviewDTO interviewDTO
    ) {
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        record.setInterviewTime(interviewDTO.getInterviewTime());
        record.setNextFollowTime(interviewDTO.getNextFollowTime());
        record.setNote(interviewDTO.getNote());
        record.setCommunicationStatus(CommunicationStatus.INTERVIEW_INVITED.name());

        jobCommunicationRecordMapper.updateById(record);

        /*
         * 更新后重新走详情关联查询，保证返回 resumeName、jobTitle、companyName。
         */
        return getDetail(userId, id);
    }

    /**
     * 关闭沟通。
     */
    @Override
    public JobCommunicationRecordVO closeCommunication(Long userId, Long id) {
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        record.setCommunicationStatus(CommunicationStatus.CLOSED.name());
        jobCommunicationRecordMapper.updateById(record);

        /*
         * 更新后重新走详情关联查询，保证返回 resumeName、jobTitle、companyName。
         */
        return getDetail(userId, id);
    }

    /**
     * 查询统计数据。
     */
    @Override
    public JobCommunicationStatsVO getStats(Long userId) {
        JobCommunicationStatsVO stats = new JobCommunicationStatsVO();

        stats.setTotalCount(countByStatus(userId, null));
        stats.setGreetingGeneratedCount(countByStatus(userId, CommunicationStatus.GREETING_GENERATED.name()));
        stats.setCopiedCount(countByStatus(userId, CommunicationStatus.COPIED.name()));
        stats.setCommunicatedCount(countByStatus(userId, CommunicationStatus.COMMUNICATED.name()));
        stats.setRepliedCount(countByStatus(userId, CommunicationStatus.REPLIED.name()));
        stats.setInterviewInvitedCount(countByStatus(userId, CommunicationStatus.INTERVIEW_INVITED.name()));
        stats.setNoReplyCount(countByStatus(userId, CommunicationStatus.NO_REPLY.name()));
        stats.setClosedCount(countByStatus(userId, CommunicationStatus.CLOSED.name()));

        return stats;
    }

    /**
     * 根据状态统计数量。
     */
    private Long countByStatus(Long userId, String status) {
        LambdaQueryWrapper<JobCommunicationRecord> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(JobCommunicationRecord::getUserId, userId)
                .eq(JobCommunicationRecord::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(status)) {
            wrapper.eq(JobCommunicationRecord::getCommunicationStatus, status);
        }

        return jobCommunicationRecordMapper.selectCount(wrapper);
    }

    /**
     * 查询并校验记录归属。
     */
    private JobCommunicationRecord getUserRecordRequired(Long userId, Long id) {
        JobCommunicationRecord record = jobCommunicationRecordMapper.selectById(id);

        if (record == null || !Integer.valueOf(NOT_DELETED).equals(record.getIsDeleted())) {
            throw new IllegalArgumentException("沟通记录不存在");
        }

        if (!userId.equals(record.getUserId())) {
            throw new SecurityException("无权访问该沟通记录");
        }

        return record;
    }

    /**
     * Entity 转 VO。
     */
    private JobCommunicationRecordVO toVO(JobCommunicationRecord record) {
        JobCommunicationRecordVO vo = new JobCommunicationRecordVO();

        vo.setId(record.getId());
        vo.setApplicationId(record.getApplicationId());
        vo.setResumeId(record.getResumeId());
        vo.setJobId(record.getJobId());
        vo.setGreetingRecordId(record.getGreetingRecordId());

        /*
         * 第一版先不联查岗位和公司。
         * 如果你想在列表显示岗位名称和公司名称，
         * 后续可以注入 JobPositionMapper 和 CompanyMapper 做补全。
         */
        vo.setJobTitle(null);
        vo.setCompanyName(null);

        vo.setPlatform(record.getPlatform());
        vo.setExternalJobUrl(record.getExternalJobUrl());
        vo.setHrName(record.getHrName());
        vo.setHrContact(record.getHrContact());
        vo.setGreetingText(record.getGreetingText());
        vo.setHrReply(record.getHrReply());
        vo.setCommunicationStatus(record.getCommunicationStatus());
        vo.setCommunicationStatusDesc(getStatusDesc(record.getCommunicationStatus()));
        vo.setInterviewTime(record.getInterviewTime());
        vo.setNextFollowTime(record.getNextFollowTime());
        vo.setNote(record.getNote());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());

        return vo;
    }

    /**
     * 状态描述。
     */
    private String getStatusDesc(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }

        try {
            return CommunicationStatus.valueOf(status).getDesc();
        } catch (Exception e) {
            return status;
        }
    }

    /**
     * 保存沟通消息流水。
     */
    private void saveMessage(
            Long userId,
            Long communicationId,
            String senderType,
            String content,
            String replyStyle,
            String statusAfter
    ) {
        JobCommunicationMessage message = new JobCommunicationMessage();

        message.setUserId(userId);
        message.setCommunicationId(communicationId);
        message.setSenderType(senderType);
        message.setMessageContent(content);
        message.setReplyStyle(replyStyle);
        message.setStatusAfter(statusAfter);
        message.setIsDeleted(NOT_DELETED);

        jobCommunicationMessageMapper.insert(message);
    }

    /**
     * 消息实体转 VO。
     */
    private JobCommunicationMessageVO toMessageVO(JobCommunicationMessage message) {
        JobCommunicationMessageVO vo = new JobCommunicationMessageVO();

        vo.setId(message.getId());
        vo.setCommunicationId(message.getCommunicationId());
        vo.setSenderType(message.getSenderType());
        vo.setSenderTypeDesc(getMessageTypeDesc(message.getSenderType()));
        vo.setMessageContent(message.getMessageContent());
        vo.setReplyStyle(message.getReplyStyle());
        vo.setStatusAfter(message.getStatusAfter());
        vo.setCreateTime(message.getCreateTime());

        return vo;
    }

    /**
     * 消息类型中文。
     */
    private String getMessageTypeDesc(String senderType) {
        if (CommunicationMessageType.HR_TO_USER.name().equals(senderType)) {
            return "HR回复";
        }

        if (CommunicationMessageType.AI_SUGGESTION.name().equals(senderType)) {
            return "AI建议回复";
        }

        if (CommunicationMessageType.USER_TO_HR.name().equals(senderType)) {
            return "已发送给HR";
        }

        if ("STATUS_CHANGE".equals(senderType)) {
            return "状态变更";
        }

        return senderType;
    }
}