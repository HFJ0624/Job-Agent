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
 * 核心原则：
 * 1. 识别阶段只读上下文、调用模型、保存 PENDING 记录，不修改业务状态。
 * 2. 确认阶段才执行用户勾选的动作，避免 AI 误判直接污染求职进度。
 * 3. 面试邀约复用已有提醒和求职进度同步链路，不另写一套分叉逻辑。
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

    private JsonNode parseRecognitionJson(String json) {
        try {
            return objectMapper.readTree(cleanJson(json));
        } catch (Exception ex) {
            throw new BizException("HR 回复识别结果解析失败，AI 返回内容：" + json);
        }
    }

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

    private String resolveCommunicationStatus(String intentType) {
        if ("INTERVIEW_INVITE".equals(intentType)) {
            return CommunicationStatus.INTERVIEW_INVITED.name();
        }
        if ("REJECTED".equals(intentType)) {
            return CommunicationStatus.CLOSED.name();
        }
        return CommunicationStatus.REPLIED.name();
    }

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
