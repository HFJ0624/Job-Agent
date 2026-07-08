package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.HrReplyRecognitionRecordMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.JobCommunicationMessageMapper;
import com.job.bootstrap.mapper.JobCommunicationRecordMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.HrReplyRecognitionService;
import com.job.bootstrap.service.JobApplicationService;
import com.job.bootstrap.service.JobReminderService;
import com.job.common.dto.application.JobApplicationStatusUpdateDTO;
import com.job.common.dto.communication.HrReplyRecognitionConfirmDTO;
import com.job.common.dto.communication.HrReplyRecognizeDTO;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.communication.HrReplyRecognitionRecord;
import com.job.common.entity.communication.JobCommunicationMessage;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.common.vo.communication.JobCommunicationRecordVO;
import com.job.common.vo.communication.HrReplyRecognitionVO;
import com.job.enums.CommunicationMessageType;
import com.job.enums.CommunicationStatus;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HR 回复识别服务实现。
 *
 * <p>核心职责：基于 AI 模型识别 HR 回复意图（面试邀约、需补充信息、等待、拒绝、Offer、普通回复），
 * 生成结构化识别结果供用户确认后，再驱动下游业务状态变更。</p>
 *
 * <p>所属业务模块：求职沟通管理 - HR 回复智能识别子模块</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>识别入口：{@code Controller -> recognizeFromCommunication / recognizeFromApplication}</li>
 *   <li>模型调用：{@code recognizeAndSave -> AiModelGatewayService.chat}</li>
 *   <li>结果确认：{@code Controller -> confirm -> [updateCommunicationByRecognition / updateApplicationByRecognition]}</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>{@link AiModelGatewayService}：统一模型网关，负责 Prompt 路由和调用日志</li>
 *   <li>{@link JobApplicationService}：确认阶段同步求职进度状态</li>
 *   <li>{@link JobReminderService}：确认阶段自动生成面试/跟进提醒</li>
 *   <li>{@link JobCommunicationRecordMapper} / {@link JobCommunicationMessageMapper}：读写沟通记录和消息流水</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>识别阶段只读上下文、调用模型、保存 PENDING 记录，不修改业务状态。</li>
 *   <li>确认阶段才执行用户勾选的动作，避免 AI 误判直接污染求职进度。</li>
 *   <li>面试邀约复用已有提醒和求职进度同步链路，不另写一套分叉逻辑。</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class HrReplyRecognitionServiceImpl implements HrReplyRecognitionService {

    private static final int NOT_DELETED = 0;
    private static final String AI_SCENE_HR_REPLY_RECOGNIZE = "HR_REPLY_RECOGNIZE";
    private static final String CONFIRM_PENDING = "PENDING";
    private static final String CONFIRM_CONFIRMED = "CONFIRMED";
    private static final String CONFIRM_CANCELLED = "CANCELLED";

    private final HrReplyRecognitionRecordMapper recognitionRecordMapper;
    private final JobCommunicationRecordMapper communicationRecordMapper;
    private final JobCommunicationMessageMapper communicationMessageMapper;
    private final JobApplicationRecordMapper applicationRecordMapper;
    private final AiModelGatewayService aiModelGatewayService;
    private final JobApplicationService jobApplicationService;
    private final JobReminderService jobReminderService;
    private final ObjectMapper objectMapper;

    /**
     * 基于已有沟通记录识别 HR 回复意图。
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>校验沟通记录归属和状态。</li>
     *   <li>提取 HR 回复文本（优先使用用户传入，其次取记录中的 hrReply）。</li>
     *   <li>调用 {@link #recognizeAndSave} 执行模型识别并持久化 PENDING 记录。</li>
     *   <li>返回识别结果 VO 供前端展示和人工确认。</li>
     * </ol>
     * </p>
     *
     * @param userId         当前登录用户 ID
     * @param communicationId 沟通记录 ID
     * @param dto            识别请求，可携带用户自定义 HR 回复文本和补充说明
     * @return HR 回复识别结果，包含意图类型、建议状态、面试时间等
     * @throws BizException      沟通记录不存在或 HR 回复内容为空
     * @throws SecurityException 无权访问该沟通记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrReplyRecognitionVO recognizeFromCommunication(Long userId, Long communicationId, HrReplyRecognizeDTO dto) {
        JobCommunicationRecord record = getUserCommunicationRequired(userId, communicationId);
        JobCommunicationRecordVO detail = communicationRecordMapper.selectCommunicationDetail(userId, communicationId);
        String hrReplyText = resolveHrReply(dto, record.getHrReply());

        HrReplyRecognitionRecord recognition = recognizeAndSave(
                userId,
                record.getApplicationId(),
                communicationId,
                record.getJobId(),
                record.getResumeId(),
                detail == null ? null : detail.getCompanyName(),
                detail == null ? null : detail.getJobTitle(),
                record.getCommunicationStatus(),
                hrReplyText,
                dto == null ? null : dto.getUserNote()
        );

        return toVO(recognition);
    }

    /**
     * 基于求职记录直接识别 HR 回复意图（无沟通记录场景）。
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>校验求职记录归属。</li>
     *   <li>提取用户传入的 HR 回复文本（必须非空）。</li>
     *   <li>调用 {@link #recognizeAndSave} 执行模型识别并持久化 PENDING 记录。</li>
     * </ol>
     * </p>
     *
     * @param userId        当前登录用户 ID
     * @param applicationId 求职记录 ID
     * @param dto           识别请求，必须携带 HR 回复文本
     * @return HR 回复识别结果
     * @throws BizException 求职记录不存在或 HR 回复内容为空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrReplyRecognitionVO recognizeFromApplication(Long userId, Long applicationId, HrReplyRecognizeDTO dto) {
        JobApplicationRecord application = getUserApplicationRequired(userId, applicationId);
        String hrReplyText = resolveHrReply(dto, null);

        HrReplyRecognitionRecord recognition = recognizeAndSave(
                userId,
                applicationId,
                null,
                application.getJobId(),
                application.getResumeId(),
                application.getCompanyName(),
                application.getJobTitle(),
                application.getStatus(),
                hrReplyText,
                dto == null ? null : dto.getUserNote()
        );

        return toVO(recognition);
    }

    /**
     * 确认 HR 回复识别结果，并执行用户勾选的业务动作。
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>校验识别记录归属和状态（必须为 PENDING）。</li>
     *   <li>将用户编辑后的字段回写识别记录。</li>
     *   <li>若勾选保存沟通，更新沟通记录并写入消息流水。</li>
     *   <li>若勾选创建提醒，复用 {@link JobReminderService#syncFromCommunicationRecord} 生成提醒。</li>
     *   <li>若勾选更新求职进度，调用 {@link JobApplicationService#updateStatus} 同步状态。</li>
     *   <li>标记确认状态为 CONFIRMED，并记录已执行动作快照。</li>
     * </ol>
     * </p>
     *
     * @param userId       当前登录用户 ID
     * @param recognitionId 识别记录 ID
     * @param dto          确认请求，包含用户勾选的执行项和编辑后的字段
     * @return 更新后的识别结果
     * @throws BizException      识别记录已处理或不存在
     * @throws RuntimeException  动作执行异常时会记录 errorMsg 并重抛，保证事务回滚
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrReplyRecognitionVO confirm(Long userId, Long recognitionId, HrReplyRecognitionConfirmDTO dto) {
        HrReplyRecognitionRecord recognition = getUserRecognitionRequired(userId, recognitionId);

        if (!CONFIRM_PENDING.equals(recognition.getConfirmStatus())) {
            throw new BizException("该识别结果已经处理过，不能重复确认");
        }

        Map<String, Object> executedActions = new LinkedHashMap<>();

        try {
            /*
             * 1. 先把用户确认后的可编辑字段写回识别记录。
             *    这样即使后续动作失败，也能看到用户当时确认的值。
             */
            applyUserConfirmedFields(recognition, dto);

            /*
             * 2. 如果有沟通记录，并且用户选择保存沟通，则写入 HR 回复和消息流水。
             */
            if (Boolean.TRUE.equals(dto.getSaveCommunication()) && recognition.getCommunicationId() != null) {
                JobCommunicationRecord communication = updateCommunicationByRecognition(userId, recognition);
                executedActions.put("saveCommunication", true);

                /*
                 * 3. 如果需要提醒，复用沟通记录提醒同步逻辑。
                 *    这里会根据 interviewTime / nextFollowTime 自动生成对应提醒。
                 */
                if (Boolean.TRUE.equals(dto.getCreateReminder())) {
                    jobReminderService.syncFromCommunicationRecord(userId, communication);
                    executedActions.put("createReminder", true);
                }
            }

            /*
             * 4. 如果用户选择更新求职进度，则走已有 JobApplicationService。
             *    这样面试状态会继续触发跟进 Agent、面试准备任务、邮件通知等现有链路。
             */
            if (Boolean.TRUE.equals(dto.getUpdateApplicationStatus()) && recognition.getApplicationId() != null) {
                updateApplicationByRecognition(userId, recognition, dto);
                executedActions.put("updateApplicationStatus", true);
            }

            executedActions.put("generateInterviewPrepare", Boolean.TRUE.equals(dto.getGenerateInterviewPrepare()));
            recognition.setConfirmStatus(CONFIRM_CONFIRMED);
            recognition.setExecutedActionsJson(toJson(executedActions));
            recognition.setErrorMsg(null);
            recognitionRecordMapper.updateById(recognition);
            return toVO(recognition);
        } catch (RuntimeException ex) {
            recognition.setErrorMsg(ex.getMessage());
            recognitionRecordMapper.updateById(recognition);
            throw ex;
        }
    }

    /**
     * 取消 HR 回复识别结果。
     *
     * <p>仅允许对 PENDING 状态的识别记录执行取消操作，取消后记录不再允许确认或再次取消。</p>
     *
     * @param userId        当前登录用户 ID
     * @param recognitionId 识别记录 ID
     * @return 更新后的识别结果
     * @throws BizException 识别记录已处理或不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrReplyRecognitionVO cancel(Long userId, Long recognitionId) {
        HrReplyRecognitionRecord recognition = getUserRecognitionRequired(userId, recognitionId);

        if (!CONFIRM_PENDING.equals(recognition.getConfirmStatus())) {
            throw new BizException("该识别结果已经处理过，不能取消");
        }

        recognition.setConfirmStatus(CONFIRM_CANCELLED);
        recognitionRecordMapper.updateById(recognition);
        return toVO(recognition);
    }

    /**
     * 调用 AI 模型识别 HR 回复并持久化为 PENDING 记录。
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>构造含上下文信息的 Prompt。</li>
     *   <li>通过 {@link AiModelGatewayService#chat} 调用模型并获取 JSON 结果。</li>
     *   <li>解析模型返回的意图、建议状态、时间、待办、回复建议等字段。</li>
     *   <li>填充并保存 {@link HrReplyRecognitionRecord}，状态固定为 PENDING。</li>
     * </ol>
     * </p>
     *
     * @param userId          当前登录用户 ID
     * @param applicationId   关联求职记录 ID，可为空
     * @param communicationId 关联沟通记录 ID，可为空
     * @param jobId           岗位 ID
     * @param resumeId        简历 ID
     * @param companyName     公司名称
     * @param jobTitle        岗位名称
     * @param currentStatus   当前求职状态
     * @param hrReplyText     HR 回复原文
     * @param userNote        用户补充说明
     * @return 已持久化的识别记录
     * @throws BizException 模型返回解析失败或时间格式不合法
     */
    private HrReplyRecognitionRecord recognizeAndSave(
            Long userId,
            Long applicationId,
            Long communicationId,
            Long jobId,
            Long resumeId,
            String companyName,
            String jobTitle,
            String currentStatus,
            String hrReplyText,
            String userNote
    ) {
        String nowText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String prompt = buildPrompt(nowText, companyName, jobTitle, currentStatus, hrReplyText, userNote);

        /*
         * 统一走模型网关。
         * 如果数据库没有配置 HR_REPLY_RECOGNIZE 的模型路由，网关会直接报错，便于管理员补配置。
         */
        String json = aiModelGatewayService.chat(
                AI_SCENE_HR_REPLY_RECOGNIZE,
                buildPromptVariables(nowText, companyName, jobTitle, currentStatus, hrReplyText, userNote, prompt),
                prompt,
                userId,
                "hr_reply_recognize_" + userId + "_" + System.currentTimeMillis()
        );

        JsonNode root = parseRecognitionJson(json);

        HrReplyRecognitionRecord record = new HrReplyRecognitionRecord();
        record.setUserId(userId);
        record.setApplicationId(applicationId);
        record.setCommunicationId(communicationId);
        record.setJobId(jobId);
        record.setResumeId(resumeId);
        record.setCompanyName(companyName);
        record.setJobTitle(jobTitle);
        record.setCurrentStatus(currentStatus);
        record.setHrReplyText(hrReplyText);
        record.setIntentType(readText(root, "intentType", "GENERAL_REPLY"));
        record.setConfidence(readDecimal(root, "confidence"));
        record.setSuggestedStatus(normalizeSuggestedStatus(readText(root, "suggestedStatus", null), record.getIntentType()));
        record.setInterviewTime(parseDate(readText(root, "interviewTime", null)));
        record.setNextFollowTime(parseDate(readText(root, "nextFollowTime", null)));
        record.setTodoItemsJson(toJson(readStringList(root, "todoItems")));
        record.setReplySuggestion(readText(root, "replySuggestion", null));
        record.setReason(readText(root, "reason", null));
        record.setRecognitionJson(cleanJson(json));
        record.setConfirmStatus(CONFIRM_PENDING);
        record.setIsDeleted(NOT_DELETED);

        recognitionRecordMapper.insert(record);
        return record;
    }

    /**
     * 将用户确认时编辑的字段回写到识别记录。
     *
     * @param recognition 待更新的识别记录
     * @param dto         用户确认请求，包含编辑后的建议状态、面试时间、下次跟进时间
     */
    private void applyUserConfirmedFields(HrReplyRecognitionRecord recognition, HrReplyRecognitionConfirmDTO dto) {
        if (StringUtils.hasText(dto.getSuggestedStatus())) {
            recognition.setSuggestedStatus(normalizeSuggestedStatus(dto.getSuggestedStatus(), recognition.getIntentType()));
        }
        if (dto.getInterviewTime() != null) {
            recognition.setInterviewTime(dto.getInterviewTime());
        }
        if (dto.getNextFollowTime() != null) {
            recognition.setNextFollowTime(dto.getNextFollowTime());
        }
    }

    /**
     * 根据识别结果更新沟通记录，并写入消息流水。
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>读取并校验沟通记录归属。</li>
     *   <li>将 HR 回复、AI 回复建议、沟通状态、面试时间等回写沟通记录。</li>
     *   <li>插入 HR 回复消息和 AI 识别系统消息两条流水。</li>
     * </ol>
     * </p>
     *
     * @param userId      当前登录用户 ID
     * @param recognition 已确认的识别结果
     * @return 更新后的沟通记录
     */
    private JobCommunicationRecord updateCommunicationByRecognition(Long userId, HrReplyRecognitionRecord recognition) {
        JobCommunicationRecord communication = getUserCommunicationRequired(userId, recognition.getCommunicationId());
        String communicationStatus = resolveCommunicationStatus(recognition.getIntentType());

        communication.setHrReply(recognition.getHrReplyText());
        communication.setAiReplyText(recognition.getReplySuggestion());
        communication.setCommunicationStatus(communicationStatus);
        communication.setInterviewTime(recognition.getInterviewTime());
        communication.setNextFollowTime(recognition.getNextFollowTime());
        communication.setInterviewExtractJson(recognition.getRecognitionJson());
        communication.setInterviewExtractConfidence(
                recognition.getConfidence() == null ? null : recognition.getConfidence().doubleValue()
        );
        communicationRecordMapper.updateById(communication);

        saveCommunicationMessage(
                userId,
                communication.getId(),
                CommunicationMessageType.HR_TO_USER.name(),
                recognition.getHrReplyText(),
                communicationStatus
        );
        saveCommunicationMessage(
                userId,
                communication.getId(),
                "HR_REPLY_RECOGNIZED",
                "AI 已识别 HR 回复：" + toJson(toVO(recognition)),
                communicationStatus
        );
        return communication;
    }

    /**
     * 根据识别结果更新求职记录状态。
     *
     * <p>通过 {@link JobApplicationService#updateStatus} 复用已有状态更新链路，
     * 确保面试状态变更后继续触发跟进 Agent、面试准备任务和邮件通知。</p>
     *
     * @param userId      当前登录用户 ID
     * @param recognition 已确认的识别结果
     * @param dto         用户确认请求，可携带备注
     */
    private void updateApplicationByRecognition(Long userId, HrReplyRecognitionRecord recognition, HrReplyRecognitionConfirmDTO dto) {
        String suggestedStatus = normalizeSuggestedStatus(recognition.getSuggestedStatus(), recognition.getIntentType());

        JobApplicationStatusUpdateDTO statusDTO = new JobApplicationStatusUpdateDTO();
        statusDTO.setStatus(suggestedStatus);
        statusDTO.setInterviewTime(recognition.getInterviewTime());
        statusDTO.setNextFollowTime(recognition.getNextFollowTime());
        statusDTO.setNote(StringUtils.hasText(dto.getNote())
                ? dto.getNote()
                : "由 HR 回复识别确认更新：" + intentDesc(recognition.getIntentType()));

        jobApplicationService.updateStatus(userId, recognition.getApplicationId(), statusDTO);
    }

    /**
     * 解析 HR 回复文本。
     *
     * <p>优先级：用户传入的 dto.hrReplyText > fallback（沟通记录中的 hrReply）。
     * 两者皆空时抛出业务异常。</p>
     *
     * @param dto      识别请求 DTO，可能为空
     * @param fallback 备选 HR 回复文本
     * @return 非空且已 trim 的 HR 回复文本
     * @throws BizException HR 回复内容不能为空
     */
    private String resolveHrReply(HrReplyRecognizeDTO dto, String fallback) {
        String value = dto == null ? null : dto.getHrReplyText();
        if (!StringUtils.hasText(value)) {
            value = fallback;
        }
        if (!StringUtils.hasText(value)) {
            throw new BizException("HR 回复内容不能为空");
        }
        return value.trim();
    }

    /**
     * 获取并校验用户沟通记录。
     *
     * @param userId          当前登录用户 ID
     * @param communicationId 沟通记录 ID
     * @return 归属当前用户且未删除的沟通记录
     * @throws BizException      沟通记录不存在或 ID 为空
     * @throws SecurityException 无权访问该沟通记录
     */
    private JobCommunicationRecord getUserCommunicationRequired(Long userId, Long communicationId) {
        if (communicationId == null) {
            throw new BizException("沟通记录 ID 不能为空");
        }

        JobCommunicationRecord record = communicationRecordMapper.selectById(communicationId);
        if (record == null || !Integer.valueOf(NOT_DELETED).equals(record.getIsDeleted())) {
            throw new BizException("沟通记录不存在");
        }
        if (!userId.equals(record.getUserId())) {
            throw new SecurityException("无权访问该沟通记录");
        }
        return record;
    }

    /**
     * 获取并校验用户求职记录。
     *
     * @param userId        当前登录用户 ID
     * @param applicationId 求职记录 ID
     * @return 归属当前用户的求职记录
     * @throws BizException 求职记录不存在、无权访问或 ID 为空
     */
    private JobApplicationRecord getUserApplicationRequired(Long userId, Long applicationId) {
        if (applicationId == null) {
            throw new BizException("求职记录 ID 不能为空");
        }

        JobApplicationRecord record = applicationRecordMapper.selectById(applicationId);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new BizException("求职记录不存在或无权访问");
        }
        return record;
    }

    /**
     * 获取并校验用户 HR 回复识别记录。
     *
     * @param userId        当前登录用户 ID
     * @param recognitionId 识别记录 ID
     * @return 归属当前用户且未删除的识别记录
     * @throws BizException 识别记录不存在
     */
    private HrReplyRecognitionRecord getUserRecognitionRequired(Long userId, Long recognitionId) {
        HrReplyRecognitionRecord record = recognitionRecordMapper.selectOne(
                new LambdaQueryWrapper<HrReplyRecognitionRecord>()
                        .eq(HrReplyRecognitionRecord::getId, recognitionId)
                        .eq(HrReplyRecognitionRecord::getUserId, userId)
                        .eq(HrReplyRecognitionRecord::getIsDeleted, NOT_DELETED)
                        .last("limit 1")
        );
        if (record == null) {
            throw new BizException("HR 回复识别记录不存在");
        }
        return record;
    }

    /**
     * 构造 HR 回复识别 Prompt。
     *
     * <p>Prompt 包含系统时间、岗位上下文、HR 回复原文，要求模型只输出固定格式的 JSON。</p>
     *
     * @param nowText       当前系统时间文本
     * @param companyName   公司名称
     * @param jobTitle      岗位名称
     * @param currentStatus 当前求职状态
     * @param hrReplyText   HR 回复原文
     * @param userNote      用户补充说明
     * @return 完整的模型识别 Prompt
     */
    private String buildPrompt(
            String nowText,
            String companyName,
            String jobTitle,
            String currentStatus,
            String hrReplyText,
            String userNote
    ) {
        return """
                当前系统时间：%s

                你是求职跟进 Agent，请识别 HR 回复的意图，并给出建议动作。

                【岗位上下文】
                公司：%s
                岗位：%s
                当前状态：%s
                用户补充：%s

                【HR 回复原文】
                %s

                只输出 JSON，不要输出 Markdown，不要输出解释性文字。JSON 字段如下：
                {
                  "intentType": "INTERVIEW_INVITE | NEED_MORE_INFO | WAITING | REJECTED | OFFER | GENERAL_REPLY",
                  "confidence": 0.0,
                  "suggestedStatus": "INTERVIEWING | COMMUNICATED | APPLIED | OFFER | REJECTED | CLOSED",
                  "interviewTime": "yyyy-MM-dd HH:mm:ss 或空字符串",
                  "nextFollowTime": "yyyy-MM-dd HH:mm:ss 或空字符串",
                  "todoItems": ["待办事项1"],
                  "replySuggestion": "建议回复 HR 的中文话术",
                  "reason": "判断原因"
                }
                """.formatted(
                nowText,
                nullToDefault(companyName, "未知公司"),
                nullToDefault(jobTitle, "未知岗位"),
                nullToDefault(currentStatus, "未知状态"),
                nullToDefault(userNote, "无"),
                hrReplyText
        );
    }

    /**
     * 构造 Prompt 变量映射，供后台模板引擎引用。
     *
     * <p>同时提供驼峰和下划线两种 key 风格，兼容不同模板引用习惯。</p>
     *
     * @param nowText       当前系统时间文本
     * @param companyName   公司名称
     * @param jobTitle      岗位名称
     * @param currentStatus 当前求职状态
     * @param hrReplyText   HR 回复原文
     * @param userNote      用户补充说明
     * @param fullPrompt    完整 Prompt 文本
     * @return 供模型网关使用的变量映射
     */
    private Map<String, Object> buildPromptVariables(
            String nowText,
            String companyName,
            String jobTitle,
            String currentStatus,
            String hrReplyText,
            String userNote,
            String fullPrompt
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nowText", nowText);
        variables.put("now_text", nowText);
        variables.put("companyName", nullToDefault(companyName, "未知公司"));
        variables.put("company_name", variables.get("companyName"));
        variables.put("jobTitle", nullToDefault(jobTitle, "未知岗位"));
        variables.put("job_title", variables.get("jobTitle"));
        variables.put("currentStatus", nullToDefault(currentStatus, "未知状态"));
        variables.put("current_status", variables.get("currentStatus"));
        variables.put("hrReplyText", hrReplyText);
        variables.put("hr_reply_text", hrReplyText);
        variables.put("userNote", nullToDefault(userNote, "无"));
        variables.put("user_note", variables.get("userNote"));
        variables.put("fullPrompt", fullPrompt);
        variables.put("full_prompt", fullPrompt);
        return variables;
    }

    /**
     * 解析模型返回的识别 JSON。
     *
     * @param json 模型原始返回文本（已清洗）
     * @return Jackson JsonNode
     * @throws BizException JSON 解析失败
     */
    private JsonNode parseRecognitionJson(String json) {
        try {
            return objectMapper.readTree(cleanJson(json));
        } catch (Exception ex) {
            throw new BizException("HR 回复识别结果解析失败，AI 返回内容：" + json);
        }
    }

    /**
     * 清洗模型返回内容，去除 Markdown 代码块标记。
     *
     * @param text 模型原始返回文本
     * @return 纯 JSON 文本
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

    private String readText(JsonNode root, String field, String defaultValue) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private BigDecimal readDecimal(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(node.asDouble(0D));
    }

    private List<String> readStringList(JsonNode root, String field) {
        try {
            JsonNode node = root == null ? null : root.get(field);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            return objectMapper.convertValue(node, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Date parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value.trim());
        } catch (Exception ex) {
            throw new BizException("AI 返回的时间格式不正确：" + value);
        }
    }

    /**
     * 规范化建议求职状态，防止模型返回非法状态值。
     *
     * <p>若传入状态不在白名单内，则按意图类型返回默认状态。</p>
     *
     * @param suggestedStatus 模型建议的状态
     * @param intentType      识别出的意图类型
     * @return 合法的标准求职状态
     */
    private String normalizeSuggestedStatus(String suggestedStatus, String intentType) {
        String value = StringUtils.hasText(suggestedStatus) ? suggestedStatus.trim() : defaultStatusByIntent(intentType);
        if (!List.of("INTERESTED", "COMMUNICATED", "APPLIED", "INTERVIEWING", "OFFER", "REJECTED", "CLOSED").contains(value)) {
            return defaultStatusByIntent(intentType);
        }
        return value;
    }

    private String defaultStatusByIntent(String intentType) {
        if ("INTERVIEW_INVITE".equals(intentType)) {
            return "INTERVIEWING";
        }
        if ("OFFER".equals(intentType)) {
            return "OFFER";
        }
        if ("REJECTED".equals(intentType)) {
            return "REJECTED";
        }
        if ("WAITING".equals(intentType) || "NEED_MORE_INFO".equals(intentType)) {
            return "COMMUNICATED";
        }
        return "COMMUNICATED";
    }

    /**
     * 根据识别意图映射沟通记录状态。
     *
     * @param intentType 识别意图类型
     * @return 沟通状态枚举名称
     */
    private String resolveCommunicationStatus(String intentType) {
        if ("INTERVIEW_INVITE".equals(intentType)) {
            return CommunicationStatus.INTERVIEW_INVITED.name();
        }
        if ("REJECTED".equals(intentType)) {
            return CommunicationStatus.CLOSED.name();
        }
        return CommunicationStatus.REPLIED.name();
    }

    /**
     * 保存沟通消息流水。
     *
     * @param userId          当前登录用户 ID
     * @param communicationId 沟通记录 ID
     * @param senderType      消息发送方类型，如 HR_TO_USER、HR_REPLY_RECOGNIZED
     * @param content         消息内容
     * @param statusAfter     消息发生后的沟通状态
     */
    private void saveCommunicationMessage(Long userId, Long communicationId, String senderType, String content, String statusAfter) {
        JobCommunicationMessage message = new JobCommunicationMessage();
        message.setUserId(userId);
        message.setCommunicationId(communicationId);
        message.setSenderType(senderType);
        message.setMessageContent(content);
        message.setStatusAfter(statusAfter);
        message.setIsDeleted(NOT_DELETED);
        communicationMessageMapper.insert(message);
    }

    private HrReplyRecognitionVO toVO(HrReplyRecognitionRecord record) {
        HrReplyRecognitionVO vo = new HrReplyRecognitionVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setApplicationId(record.getApplicationId());
        vo.setCommunicationId(record.getCommunicationId());
        vo.setJobId(record.getJobId());
        vo.setResumeId(record.getResumeId());
        vo.setCompanyName(record.getCompanyName());
        vo.setJobTitle(record.getJobTitle());
        vo.setCurrentStatus(record.getCurrentStatus());
        vo.setHrReplyText(record.getHrReplyText());
        vo.setIntentType(record.getIntentType());
        vo.setIntentTypeDesc(intentDesc(record.getIntentType()));
        vo.setConfidence(record.getConfidence());
        vo.setSuggestedStatus(record.getSuggestedStatus());
        vo.setSuggestedStatusDesc(statusDesc(record.getSuggestedStatus()));
        vo.setCommunicationStatus(resolveCommunicationStatus(record.getIntentType()));
        vo.setInterviewTime(record.getInterviewTime());
        vo.setNextFollowTime(record.getNextFollowTime());
        vo.setTodoItems(parseTodoItems(record.getTodoItemsJson()));
        vo.setReplySuggestion(record.getReplySuggestion());
        vo.setReason(record.getReason());
        vo.setRecognitionJson(record.getRecognitionJson());
        vo.setConfirmStatus(record.getConfirmStatus());
        vo.setExecutedActionsJson(record.getExecutedActionsJson());
        vo.setErrorMsg(record.getErrorMsg());
        vo.setDefaultActions(defaultActions(record));
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }

    private List<String> parseTodoItems(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Boolean> defaultActions(HrReplyRecognitionRecord record) {
        Map<String, Boolean> actions = new LinkedHashMap<>();
        actions.put("saveCommunication", record.getCommunicationId() != null);
        actions.put("updateApplicationStatus", record.getApplicationId() != null);
        actions.put("createReminder", record.getInterviewTime() != null || record.getNextFollowTime() != null);
        actions.put("generateInterviewPrepare", "INTERVIEW_INVITE".equals(record.getIntentType()));
        return actions;
    }

    private String intentDesc(String intentType) {
        if ("INTERVIEW_INVITE".equals(intentType)) return "面试邀约";
        if ("NEED_MORE_INFO".equals(intentType)) return "HR 需要补充信息";
        if ("WAITING".equals(intentType)) return "等待后续";
        if ("REJECTED".equals(intentType)) return "已拒绝";
        if ("OFFER".equals(intentType)) return "Offer";
        return "普通回复";
    }

    private String statusDesc(String status) {
        if ("INTERESTED".equals(status)) return "感兴趣";
        if ("COMMUNICATED".equals(status)) return "已沟通";
        if ("APPLIED".equals(status)) return "已投递";
        if ("INTERVIEWING".equals(status)) return "面试中";
        if ("OFFER".equals(status)) return "Offer";
        if ("REJECTED".equals(status)) return "已拒绝";
        if ("CLOSED".equals(status)) return "已关闭";
        return status;
    }

    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
