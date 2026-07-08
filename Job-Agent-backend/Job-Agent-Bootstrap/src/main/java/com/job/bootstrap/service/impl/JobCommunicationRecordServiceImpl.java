package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobCommunicationMessageMapper;
import com.job.bootstrap.mapper.JobCommunicationRecordMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.JobApplicationService;
import com.job.bootstrap.service.JobCommunicationRecordService;
import com.job.bootstrap.service.JobReminderService;
import com.job.common.dto.communication.*;
import com.job.common.entity.communication.JobCommunicationMessage;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.common.vo.communication.*;
import com.job.enums.CommunicationMessageType;
import com.job.enums.CommunicationStatus;
import com.job.enums.InterviewMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 求职沟通记录服务实现。
 *
 * <p>核心职责：记录用户在外部招聘平台与 HR 沟通的全过程，支持状态流转、AI 回复生成、面试邀约信息提取和确认，并同步触发提醒和求职进度更新。</p>
 *
 * <p>所属业务模块：求职沟通模块（communication）</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>生成打招呼语后，调用 {@link #createFromGreeting} 自动创建沟通记录；</li>
 *   <li>用户复制话术到平台后，调用 {@link #markCopied} 标记已复制；</li>
 *   <li>用户收到 HR 回复后，调用 {@link #saveHrReplyAndGenerateReply} 保存 HR 回复并生成 AI 建议回复；</li>
 *   <li>用户确认面试邀约后，调用 {@link #confirmInterviewInvite} 保存面试信息并同步提醒；</li>
 *   <li>沟通结束时，调用 {@link #closeCommunication} 关闭记录。</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link AiModelGatewayService} 生成 AI 回复和提取面试邀约信息；</li>
 *   <li>依赖 {@link JobReminderService} 在面试确认后自动同步面试/跟进提醒；</li>
 *   <li>依赖 {@link JobApplicationService} 同步求职进度；</li>
 *   <li>依赖 {@link JobCommunicationRecordMapper} 和 {@link JobCommunicationMessageMapper} 进行数据持久化。</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>每条沟通记录维护一条消息流水（job_communication_message），便于前端时间线展示；</li>
 *   <li>状态变更统一收口，禁止 Controller 直接修改状态；</li>
 *   <li>AI 回复生成和面试邀约提取均通过统一模型网关调用，不依赖本地配置；</li>
 *   <li>面试确认后自动联动提醒和求职进度，保证多模块数据一致性。</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class JobCommunicationRecordServiceImpl implements JobCommunicationRecordService {

    private static final int NOT_DELETED = 0;

    /**
     * HR 回复生成模型场景。
     */
    private static final String AI_SCENE_HR_REPLY_GENERATE = "HR_REPLY_GENERATE";

    /**
     * 面试邀约抽取模型场景。
     */
    private static final String AI_SCENE_INTERVIEW_INVITE_EXTRACT = "INTERVIEW_INVITE_EXTRACT";

    private final JobCommunicationRecordMapper jobCommunicationRecordMapper;

    private final JobCommunicationMessageMapper jobCommunicationMessageMapper;

    private final AiModelGatewayService aiModelGatewayService;

    private final JobReminderService jobReminderService;

    private final JobApplicationService jobApplicationService;

    private final ObjectMapper objectMapper;

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

    /**
     * 查询沟通记录下的消息流水列表。
     *
     * @param userId          用户 ID
     * @param communicationId 沟通记录 ID
     * @return 消息 VO 列表
     */
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

    /**
     * 更新沟通状态并保存状态变更消息流水，同步触发提醒。
     *
     * @param userId 用户 ID
     * @param id     沟通记录 ID
     * @param dto    状态更新 DTO
     * @return 更新后的沟通记录 VO
     */
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

        jobReminderService.syncFromCommunicationRecord(userId,record);

        return getDetail(userId, id);
    }

    /**
     * 标记用户已发送回复给 HR，保存消息流水并更新沟通状态。
     *
     * @param userId 用户 ID
     * @param id     沟通记录 ID
     * @param dto    用户回复 DTO
     * @return 更新后的沟通记录 VO
     */
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewInviteExtractVO extractInterviewInvite(
            Long userId,
            Long id,
            InterviewInviteExtractDTO dto
    ) {
        /*
         * 1. 查询并校验沟通记录归属。
         *    防止 A 用户通过 communicationId 提取 B 用户的 HR 回复。
         */
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        /*
         * 2. 获取待提取的 HR 回复文本。
         *    优先使用请求 DTO 中传入的 hrReply。
         *    如果没有传，则使用沟通记录里已经保存的 hrReply。
         */
        String hrReplyText = null;

        if (dto != null && StringUtils.hasText(dto.getHrReply())) {
            hrReplyText = dto.getHrReply();
        } else {
            hrReplyText = record.getHrReply();
        }

        if (!StringUtils.hasText(hrReplyText)) {
            throw new IllegalArgumentException("HR回复内容为空，无法提取面试邀约信息");
        }

        /*
         * 3. 构造 Prompt。
         *    这里把当前日期时间也传给模型。
         *    这样模型才能把“明天”“周三”“下周一”转换成具体日期。
         */
        String nowText = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date());

        String prompt = """
            当前系统时间：%s
            
            请从下面 HR 回复中提取面试邀约信息：
            
            【HR回复】
            %s
            
            请严格按系统要求输出 JSON。
            """.formatted(nowText, hrReplyText);

        /*
         * 4. 调用统一模型网关进行结构化抽取。
         *    模型、Prompt、重试、熔断和日志都从数据库读取，不再依赖本地 job.ai 配置。
         */
        String json = aiModelGatewayService.chat(
                AI_SCENE_INTERVIEW_INVITE_EXTRACT,
                buildInterviewExtractVariables(nowText, hrReplyText, prompt),
                prompt,
                userId,
                buildCommunicationTraceId(AI_SCENE_INTERVIEW_INVITE_EXTRACT, userId, id)
        );

        /*
         * 5. 解析 AI 返回的 JSON。
         *    注意：
         *    有些模型可能会返回 ```json 包裹，因此这里做一次清洗。
         */
        InterviewInviteExtractVO extractVO = parseExtractJson(json);

        /*
         * 6. 对提取结果做兜底补全。
         */
        fillExtractDefault(extractVO);

        /*
         * 7. 保存 AI 提取原始结果到主记录。
         *    这样后续排查时，可以看到模型当时提取了什么。
         */
        record.setInterviewExtractJson(toJson(extractVO));
        record.setInterviewExtractConfidence(extractVO.getConfidence());

        /*
         * 8. 如果 AI 判断存在面试邀约，可以先把部分字段预填到主记录。
         *    但注意：这里不是最终确认。
         *    前端仍然应该让用户确认或修改。
         */
        if (Boolean.TRUE.equals(extractVO.getInterviewInvited())) {
            record.setCommunicationStatus(CommunicationStatus.INTERVIEW_INVITED.name());
            record.setInterviewMethod(extractVO.getInterviewMethod());
            record.setInterviewLocation(extractVO.getInterviewLocation());
            record.setInterviewPlatform(extractVO.getInterviewPlatform());
            record.setMeetingLink(extractVO.getMeetingLink());
            record.setInterviewContact(extractVO.getInterviewContact());

            /*
             * 如果 AI 成功给出具体 interviewTime，则尝试解析成 Date 保存。
             */
            record.setInterviewTime(parseDate(extractVO.getInterviewTime()));
        }

        jobCommunicationRecordMapper.updateById(record);

        /*
         * 9. 保存一条消息流水，表示系统已经提取过面试邀约。
         *    如果你前面已经做了 job_communication_message，这里可以记录。
         */
        saveMessage(
                userId,
                id,
                "INTERVIEW_EXTRACT",
                "AI已从HR回复中提取面试邀约信息：" + toJson(extractVO),
                null,
                record.getCommunicationStatus()
        );

        return extractVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCommunicationRecordVO confirmInterviewInvite(
            Long userId,
            Long id,
            InterviewInviteConfirmDTO dto
    ) {
        JobCommunicationRecord record = getUserRecordRequired(userId, id);

        /*
         * 1. 保存用户确认后的面试信息。
         */
        record.setInterviewTime(dto.getInterviewTime());
        record.setInterviewMethod(
                StringUtils.hasText(dto.getInterviewMethod())
                        ? dto.getInterviewMethod()
                        : InterviewMethod.UNKNOWN.name()
        );
        record.setInterviewLocation(dto.getInterviewLocation());
        record.setInterviewPlatform(dto.getInterviewPlatform());
        record.setMeetingLink(dto.getMeetingLink());
        record.setInterviewContact(dto.getInterviewContact());
        record.setNextFollowTime(dto.getNextFollowTime());
        record.setNote(dto.getNote());

        /*
         * 2. 状态进入面试邀约。
         */
        record.setCommunicationStatus(CommunicationStatus.INTERVIEW_INVITED.name());

        jobCommunicationRecordMapper.updateById(record);

        /*
         * 3. 保存消息流水。
         */
        saveMessage(
                userId,
                id,
                "INTERVIEW_CONFIRMED",
                "用户已确认面试信息，面试时间：" + dto.getInterviewTime(),
                null,
                CommunicationStatus.INTERVIEW_INVITED.name()
        );

        /*
         * 4. 同步创建提醒。
         *
         * 如果 interviewTime 不为空，会创建面试提醒。
         * 如果 nextFollowTime 不为空，会创建跟进提醒。
         */
        jobReminderService.syncFromCommunicationRecord(userId, record);

        jobApplicationService.syncInterviewProgress(
                userId,
                record.getApplicationId(),
                record.getInterviewTime(),
                record.getNextFollowTime()
        );

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

        return aiModelGatewayService.chat(
                AI_SCENE_HR_REPLY_GENERATE,
                buildHrReplyVariables(detail, record, dto, replyStyle, userRequirement, prompt),
                prompt,
                record.getUserId(),
                buildCommunicationTraceId(AI_SCENE_HR_REPLY_GENERATE, record.getUserId(), record.getId())
        );
    }

    /**
     * 构造面试邀约抽取 Prompt 变量。
     *
     * 方法步骤:
     * 1. 把系统时间、HR 回复拆成变量，方便后台模板灵活调整。
     * 2. 保留 fullPrompt，保证第一版模板可以直接复用当前 Java 构造好的完整输入。
     */
    private Map<String, Object> buildInterviewExtractVariables(String nowText, String hrReplyText, String fullPrompt) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nowText", nowText);
        variables.put("now_text", nowText);
        variables.put("hrReply", hrReplyText);
        variables.put("hr_reply", hrReplyText);
        variables.put("fullPrompt", fullPrompt);
        variables.put("full_prompt", fullPrompt);
        variables.put("jsonFormat", "只输出 JSON 对象，不要 Markdown，不要解释文本。");
        variables.put("json_format", variables.get("jsonFormat"));
        return variables;
    }

    /**
     * 构造 HR 回复生成 Prompt 变量。
     *
     * 方法步骤:
     * 1. 将岗位、公司、薪资、简历、HR 回复等上下文字段拆给后台 Prompt 使用。
     * 2. 保留 fullPrompt 作为兼容入口，避免第一版数据库模板必须一次性改得很复杂。
     */
    private Map<String, Object> buildHrReplyVariables(
            JobCommunicationRecordVO detail,
            JobCommunicationRecord record,
            HrReplyGenerateDTO dto,
            String replyStyle,
            String userRequirement,
            String fullPrompt
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("companyName", nullToDefault(detail.getCompanyName(), "未知公司"));
        variables.put("company_name", variables.get("companyName"));
        variables.put("jobTitle", nullToDefault(detail.getJobTitle(), "未知岗位"));
        variables.put("job_title", variables.get("jobTitle"));
        variables.put("jobCity", nullToDefault(detail.getJobCity(), "未知城市"));
        variables.put("job_city", variables.get("jobCity"));
        variables.put("salaryText", nullToDefault(detail.getSalaryText(), "薪资面议"));
        variables.put("salary_text", variables.get("salaryText"));
        variables.put("resumeName", nullToDefault(detail.getResumeName(), "未关联简历"));
        variables.put("resume_name", variables.get("resumeName"));
        variables.put("greetingText", nullToDefault(record.getGreetingText(), "无"));
        variables.put("greeting_text", variables.get("greetingText"));
        variables.put("hrReply", dto.getHrReply());
        variables.put("hr_reply", dto.getHrReply());
        variables.put("replyStyle", replyStyle);
        variables.put("reply_style", replyStyle);
        variables.put("userRequirement", userRequirement);
        variables.put("user_requirement", userRequirement);
        variables.put("fullPrompt", fullPrompt);
        variables.put("full_prompt", fullPrompt);
        return variables;
    }

    /**
     * 构建模型调用 Trace ID，用于日志串联和链路追踪。
     *
     * @param sceneCode       场景编码
     * @param userId          用户 ID
     * @param communicationId 沟通记录 ID
     * @return Trace ID 字符串
     */
    private String buildCommunicationTraceId(String sceneCode, Long userId, Long communicationId) {
        return sceneCode.toLowerCase()
                + "_"
                + userId
                + "_"
                + communicationId
                + "_"
                + System.currentTimeMillis();
    }

    /**
     * 字符串空值兜底，无有效内容时返回默认值。
     *
     * @param value        原始字符串
     * @param defaultValue 默认值
     * @return 有效字符串或默认值
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
         * 填充面试方式中文。
         */
        vo.setInterviewMethodDesc(getInterviewMethodDesc(vo.getInterviewMethod()));

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
     * 查询沟通记录详情，包含关联的岗位、公司和简历名称。
     *
     * @param userId 用户 ID
     * @param id     沟通记录 ID
     * @return 沟通记录详情 VO
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
     *
     * @param userId    用户 ID
     * @param createDTO 创建 DTO
     * @return 创建后的沟通记录 VO
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
     * 生成打招呼语后自动创建沟通记录，保证业务链路不断裂。
     *
     * @param userId           用户 ID
     * @param resumeId         简历 ID
     * @param jobId            岗位 ID
     * @param greetingRecordId 打招呼记录 ID
     * @param greetingText     打招呼语内容
     * @return 创建后的沟通记录 VO
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
     * 标记沟通记录为已复制状态。
     *
     * @param userId 用户 ID
     * @param id     沟通记录 ID
     * @return 更新后的沟通记录 VO
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
     * 标记沟通记录为已沟通状态。
     *
     * @param userId 用户 ID
     * @param id     沟通记录 ID
     * @return 更新后的沟通记录 VO
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
     * 保存 HR 回复并更新沟通状态为已回复。
     *
     * @param userId  用户 ID
     * @param id      沟通记录 ID
     * @param replyDTO HR 回复 DTO
     * @return 更新后的沟通记录 VO
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
     * 标记沟通记录为邀约面试状态，同步创建提醒。
     *
     * @param userId       用户 ID
     * @param id           沟通记录 ID
     * @param interviewDTO 面试信息 DTO
     * @return 更新后的沟通记录 VO
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

        jobReminderService.syncFromCommunicationRecord(userId,record);

        return getDetail(userId, id);
    }

    /**
     * 关闭沟通记录。
     *
     * @param userId 用户 ID
     * @param id     沟通记录 ID
     * @return 更新后的沟通记录 VO
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
     * 查询沟通记录统计数据，按状态分组计数。
     *
     * @param userId 用户 ID
     * @return 沟通统计 VO
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

    /**
     * 解析 AI 返回的 JSON。
     *
     * @param json AI 返回内容
     * @return 提取结果
     */
    private InterviewInviteExtractVO parseExtractJson(String json) {
        try {
            String cleanJson = cleanJson(json);
            return objectMapper.readValue(cleanJson, InterviewInviteExtractVO.class);
        } catch (Exception e) {
            throw new RuntimeException("面试邀约信息解析失败，AI返回内容：" + json, e);
        }
    }

    /**
     * 清洗模型返回内容。
     *
     * 有些模型可能返回:
     * ```json
     * {...}
     * ```
     *
     * 这里把 markdown 包裹去掉。
     */
    private String cleanJson(String text) {
        if (text == null) {
            return "{}";
        }

        String result = text.trim();

        if (result.startsWith("```json")) {
            result = result.replaceFirst("```json", "");
        }

        if (result.startsWith("```")) {
            result = result.replaceFirst("```", "");
        }

        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }

        return result.trim();
    }

    /**
     * 给提取结果补默认值。
     */
    private void fillExtractDefault(InterviewInviteExtractVO vo) {
        if (vo.getInterviewInvited() == null) {
            vo.setInterviewInvited(false);
        }

        if (!StringUtils.hasText(vo.getInterviewMethod())) {
            vo.setInterviewMethod(InterviewMethod.UNKNOWN.name());
            vo.setInterviewMethodDesc(InterviewMethod.UNKNOWN.getDesc());
        }

        if (vo.getNeedUserConfirm() == null) {
            vo.setNeedUserConfirm(true);
        }

        if (vo.getConfidence() == null) {
            vo.setConfidence(0D);
        }

        if (!StringUtils.hasText(vo.getReason())) {
            vo.setReason("AI未提供提取说明");
        }
    }

    /**
     * 字符串转 Date。
     *
     * @param value yyyy-MM-dd HH:mm:ss
     * @return Date
     */
    private Date parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 对象转 JSON。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 获取面试方式中文。
     */
    private String getInterviewMethodDesc(String method) {
        if (!StringUtils.hasText(method)) {
            return "";
        }

        try {
            return InterviewMethod.valueOf(method).getDesc();
        } catch (Exception e) {
            return method;
        }
    }
}
