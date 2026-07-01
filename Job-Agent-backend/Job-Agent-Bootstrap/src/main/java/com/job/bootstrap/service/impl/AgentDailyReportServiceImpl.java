package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.AgentActionItemMapper;
import com.job.bootstrap.mapper.AgentDailyReportRecordMapper;
import com.job.bootstrap.mapper.AgentDailyReportSubscriptionMapper;
import com.job.bootstrap.mapper.JobUserMapper;
import com.job.bootstrap.service.AgentDailyReportService;
import com.job.bootstrap.service.AgentInboxService;
import com.job.bootstrap.service.JobMailSenderService;
import com.job.common.dto.agent.AgentDailyReportSubscriptionSaveDTO;
import com.job.common.entity.agent.AgentActionItem;
import com.job.common.entity.agent.AgentDailyReportRecord;
import com.job.common.entity.agent.AgentDailyReportSubscription;
import com.job.common.entity.user.JobUser;
import com.job.common.vo.agent.AgentDailyReportSubscriptionVO;
import com.job.common.vo.agent.AgentDailyReportVO;
import com.job.common.vo.agent.AgentInboxVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 主动日报服务实现。
 *
 * 设计说明：
 * 1. 日报内容复用 Agent Inbox，避免再写一套“待办扫描”逻辑导致两边口径不一致。
 * 2. userId + reportDate 做幂等：定时任务重复执行或用户手动点击生成，都只会更新当天同一条日报。
 * 3. 邮件发送失败不会回滚日报记录，因为“日报已生成”和“邮件发送失败”是两个不同事实，都应该被保存。
 */
@Service
@RequiredArgsConstructor
public class AgentDailyReportServiceImpl implements AgentDailyReportService {

    private static final int NOT_DELETED = 0;
    private static final int STATUS_NORMAL = 1;
    private static final int ENABLED = 1;
    private static final String EMAIL_PENDING = "PENDING";
    private static final String EMAIL_SENT = "SENT";
    private static final String EMAIL_SKIPPED = "SKIPPED";
    private static final String EMAIL_FAILED = "FAILED";
    private static final String GENERATION_PENDING = "PENDING";
    private static final String GENERATION_SUCCESS = "SUCCESS";
    private static final String GENERATION_FAILED = "FAILED";
    private static final String SOURCE_LLM = "LLM";
    private static final String DEFAULT_SEND_TIME = "09:00";
    private static final int MAX_SCHEDULE_USER_COUNT = 500;
    private static final DateTimeFormatter SEND_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AgentDailyReportRecordMapper reportRecordMapper;
    private final AgentActionItemMapper actionItemMapper;
    private final AgentDailyReportSubscriptionMapper subscriptionMapper;
    private final JobUserMapper jobUserMapper;
    private final AgentInboxService agentInboxService;
    private final AgentDailyReportAiComposer aiComposer;
    private final AgentActionItemFactory actionItemFactory;
    private final JobMailSenderService mailSenderService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDailyReportVO generateForUser(Long userId, Date reportDate, boolean sendEmail) {
        Date normalizedReportDate = normalizeDate(reportDate);
        JobUser user = jobUserMapper.selectById(userId);
        if (user == null || !Integer.valueOf(NOT_DELETED).equals(user.getIsDeleted())) {
            throw new IllegalArgumentException("用户不存在，无法生成 Agent 日报");
        }

        AgentInboxVO inbox = agentInboxService.getTodayInbox(userId);
        AgentDailyReportRecord record = findByUserAndDate(userId, normalizedReportDate);
        if (record == null) {
            record = new AgentDailyReportRecord();
            record.setUserId(userId);
            record.setReportDate(normalizedReportDate);
            record.setIsDeleted(NOT_DELETED);
            record.setCreateTime(new Date());
        }

        /*
         * 1. 先保存 PENDING 快照，确保模型失败时也能落库记录失败原因。
         */
        preparePendingRecord(record, inbox, user);
        saveRecord(record);

        /*
         * 2. 第二版日报必须由模型生成。模型失败或 JSON 解析失败时，不使用规则日报兜底。
         */
        try {
            AgentDailyReportAiComposer.AiComposeResult composeResult = aiComposer.compose(userId, inbox);
            fillAiReportSnapshot(record, composeResult);
            record.setGenerationStatus(GENERATION_SUCCESS);
            record.setGenerationSource(SOURCE_LLM);
            record.setGenerationError(null);
            record.setUpdateTime(new Date());
            reportRecordMapper.updateById(record);
            createActionItemsFromDailyReport(record);
        } catch (Exception exception) {
            record.setGenerationStatus(GENERATION_FAILED);
            record.setGenerationSource(SOURCE_LLM);
            record.setGenerationError(shortText(exception.getMessage(), 1000));
            record.setEmailStatus(EMAIL_SKIPPED);
            record.setEmailError("AI 日报生成失败，未发送邮件");
            record.setUpdateTime(new Date());
            reportRecordMapper.updateById(record);
            throw exception;
        }

        if (sendEmail) {
            sendReportEmail(record, user);
        } else {
            record.setEmailStatus(EMAIL_SKIPPED);
            record.setEmailError("本次只生成日报，未请求发送邮件");
            reportRecordMapper.updateById(record);
        }

        return toVO(record);
    }

    @Override
    public List<AgentDailyReportVO> listRecent(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return reportRecordMapper.selectList(
                        new LambdaQueryWrapper<AgentDailyReportRecord>()
                                .eq(AgentDailyReportRecord::getUserId, userId)
                                .eq(AgentDailyReportRecord::getIsDeleted, NOT_DELETED)
                                .orderByDesc(AgentDailyReportRecord::getReportDate)
                                .orderByDesc(AgentDailyReportRecord::getCreateTime)
                                .last("limit " + safeLimit)
                )
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public AgentDailyReportSubscriptionVO getSubscription(Long userId) {
        return toSubscriptionVO(getOrCreateSubscription(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDailyReportSubscriptionVO saveSubscription(Long userId, AgentDailyReportSubscriptionSaveDTO dto) {
        AgentDailyReportSubscription subscription = getOrCreateSubscription(userId);
        subscription.setEnabled(normalizeSwitch(dto == null ? null : dto.getEnabled(), ENABLED));
        subscription.setEmailEnabled(normalizeSwitch(dto == null ? null : dto.getEmailEnabled(), ENABLED));
        subscription.setSendTime(normalizeSendTime(dto == null ? null : dto.getSendTime()));
        subscription.setUpdateTime(new Date());
        subscriptionMapper.updateById(subscription);
        return toSubscriptionVO(subscription);
    }

    @Override
    public int generateDueSubscriptions() {
        String currentTime = LocalTime.now().format(SEND_TIME_FORMATTER);
        Date today = normalizeDate(new Date());
        List<AgentDailyReportSubscription> subscriptions = subscriptionMapper.selectList(
                new LambdaQueryWrapper<AgentDailyReportSubscription>()
                        .eq(AgentDailyReportSubscription::getIsDeleted, NOT_DELETED)
                        .eq(AgentDailyReportSubscription::getEnabled, ENABLED)
                        .eq(AgentDailyReportSubscription::getSendTime, currentTime)
                        .last("limit " + MAX_SCHEDULE_USER_COUNT)
        );

        int successCount = 0;
        for (AgentDailyReportSubscription subscription : subscriptions) {
            if (alreadyGeneratedToday(subscription, today)) {
                continue;
            }
            try {
                generateForUser(subscription.getUserId(), today, Integer.valueOf(ENABLED).equals(subscription.getEmailEnabled()));
                subscription.setLastGenerateDate(today);
                subscription.setUpdateTime(new Date());
                subscriptionMapper.updateById(subscription);
                successCount++;
            } catch (Exception ignored) {
                /*
                 * 单个用户日报失败不能阻断整批调度。
                 * 失败日报会在 generateForUser 内记录为 FAILED，便于用户和后台排查。
                 */
            }
        }
        return successCount;
    }

    private void preparePendingRecord(AgentDailyReportRecord record, AgentInboxVO inbox, JobUser user) {
        record.setReportTitle("今日求职 Agent 日报");
        record.setSummaryText("AI 日报生成中");
        record.setContentText(null);
        record.setContentJson(null);
        record.setGenerationStatus(GENERATION_PENDING);
        record.setGenerationSource(SOURCE_LLM);
        record.setGenerationError(null);
        record.setInboxTotalCount(defaultInt(inbox.getTotalCount()));
        record.setHighPriorityCount(defaultInt(inbox.getHighPriorityCount()));
        record.setDueCount(defaultInt(inbox.getDueCount()));
        record.setEmailStatus(EMAIL_PENDING);
        record.setEmailError(null);
        record.setEmailTo(trimToNull(user.getEmail()));
        record.setUpdateTime(new Date());
    }

    private void fillAiReportSnapshot(
            AgentDailyReportRecord record,
            AgentDailyReportAiComposer.AiComposeResult composeResult
    ) {
        record.setReportTitle(composeResult.title());
        record.setSummaryText(composeResult.summary());
        record.setContentText(composeResult.content());
        record.setContentJson(composeResult.contentJson());
    }

    private void saveRecord(AgentDailyReportRecord record) {
        if (record.getId() == null) {
            reportRecordMapper.insert(record);
        } else {
            reportRecordMapper.updateById(record);
        }
    }

    private void sendReportEmail(AgentDailyReportRecord record, JobUser user) {
        String email = trimToNull(user.getEmail());
        if (!StringUtils.hasText(email)) {
            record.setEmailStatus(EMAIL_SKIPPED);
            record.setEmailError("用户未配置邮箱");
            reportRecordMapper.updateById(record);
            return;
        }

        try {
            /*
             * 邮件正文直接使用纯文本日报，保证不同邮箱客户端都能正常展示。
             */
            mailSenderService.sendText(email, record.getReportTitle(), record.getContentText());
            record.setEmailTo(email);
            record.setEmailStatus(EMAIL_SENT);
            record.setEmailError(null);
            record.setSendTime(new Date());
        } catch (Exception exception) {
            record.setEmailStatus(EMAIL_FAILED);
            record.setEmailError(shortText(exception.getMessage(), 1000));
        }
        reportRecordMapper.updateById(record);
    }

    private void createActionItemsFromDailyReport(AgentDailyReportRecord record) {
        List<AgentActionItemFactory.ExecutableActionSpec> executableActions = parseExecutableActions(record.getContentJson());
        List<AgentActionItem> items = executableActions.isEmpty()
                ? actionItemFactory.fromDailyReportTopActions(record.getUserId(), record.getId(), parseTopActions(record.getContentJson()))
                : actionItemFactory.fromDailyReportExecutableActions(record.getUserId(), record.getId(), executableActions);

        /*
         * V1 行动项只做确认追踪。这里按 actionKey 幂等插入，避免用户重复生成同一天日报后出现重复行动。
         */
        for (AgentActionItem item : items) {
            Long exists = actionItemMapper.selectCount(
                    new LambdaQueryWrapper<AgentActionItem>()
                            .eq(AgentActionItem::getUserId, item.getUserId())
                            .eq(AgentActionItem::getActionKey, item.getActionKey())
                            .eq(AgentActionItem::getIsDeleted, NOT_DELETED)
            );
            if (exists == null || exists == 0) {
                actionItemMapper.insert(item);
            }
        }
    }

    private List<String> parseTopActions(String contentJson) {
        List<String> actions = new ArrayList<>();
        if (!StringUtils.hasText(contentJson)) {
            return actions;
        }

        try {
            JsonNode topActions = objectMapper.readTree(contentJson).path("topActions");
            if (!topActions.isArray()) {
                return actions;
            }
            for (JsonNode actionNode : topActions) {
                String action = actionNode.asText();
                if (StringUtils.hasText(action)) {
                    actions.add(action.trim());
                }
            }
            return actions;
        } catch (Exception exception) {
            return actions;
        }
    }

    private List<AgentActionItemFactory.ExecutableActionSpec> parseExecutableActions(String contentJson) {
        List<AgentActionItemFactory.ExecutableActionSpec> actions = new ArrayList<>();
        if (!StringUtils.hasText(contentJson)) {
            return actions;
        }

        try {
            JsonNode executableActions = objectMapper.readTree(contentJson).path("executableActions");
            if (!executableActions.isArray()) {
                return actions;
            }
            for (JsonNode actionNode : executableActions) {
                AgentActionItemFactory.ExecutableActionSpec spec = new AgentActionItemFactory.ExecutableActionSpec();
                spec.setActionTitle(text(actionNode, "actionTitle"));
                spec.setActionDesc(text(actionNode, "actionDesc"));
                spec.setActionType(text(actionNode, "actionType"));
                spec.setBizType(text(actionNode, "bizType"));
                spec.setBizId(actionNode.path("bizId").isNumber() ? actionNode.path("bizId").asLong() : null);
                spec.setActionPayload(jsonText(actionNode.path("actionPayload")));
                spec.setPriority(text(actionNode, "priority"));
                spec.setTargetPath(text(actionNode, "targetPath"));
                if (StringUtils.hasText(spec.getActionTitle())) {
                    actions.add(spec);
                }
            }
            return actions;
        } catch (Exception exception) {
            return actions;
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String jsonText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText();
            return StringUtils.hasText(value) ? value.trim() : null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return null;
        }
    }

    private AgentDailyReportRecord findByUserAndDate(Long userId, Date reportDate) {
        return reportRecordMapper.selectOne(
                new LambdaQueryWrapper<AgentDailyReportRecord>()
                        .eq(AgentDailyReportRecord::getUserId, userId)
                        .eq(AgentDailyReportRecord::getReportDate, reportDate)
                        .eq(AgentDailyReportRecord::getIsDeleted, NOT_DELETED)
                        .last("limit 1")
        );
    }

    private AgentDailyReportVO toVO(AgentDailyReportRecord record) {
        AgentDailyReportVO vo = new AgentDailyReportVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setReportDate(record.getReportDate());
        vo.setReportTitle(record.getReportTitle());
        vo.setSummaryText(record.getSummaryText());
        vo.setContentText(record.getContentText());
        vo.setGenerationStatus(record.getGenerationStatus());
        vo.setGenerationSource(record.getGenerationSource());
        vo.setGenerationError(record.getGenerationError());
        vo.setInboxTotalCount(record.getInboxTotalCount());
        vo.setHighPriorityCount(record.getHighPriorityCount());
        vo.setDueCount(record.getDueCount());
        vo.setEmailTo(record.getEmailTo());
        vo.setEmailStatus(record.getEmailStatus());
        vo.setEmailError(record.getEmailError());
        vo.setSendTime(record.getSendTime());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private AgentDailyReportSubscription getOrCreateSubscription(Long userId) {
        AgentDailyReportSubscription subscription = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<AgentDailyReportSubscription>()
                        .eq(AgentDailyReportSubscription::getUserId, userId)
                        .eq(AgentDailyReportSubscription::getIsDeleted, NOT_DELETED)
                        .last("limit 1")
        );
        if (subscription != null) {
            return subscription;
        }

        Date now = new Date();
        subscription = new AgentDailyReportSubscription();
        subscription.setUserId(userId);
        subscription.setEnabled(ENABLED);
        subscription.setSendTime(DEFAULT_SEND_TIME);
        subscription.setEmailEnabled(ENABLED);
        subscription.setIsDeleted(NOT_DELETED);
        subscription.setCreateTime(now);
        subscription.setUpdateTime(now);
        subscriptionMapper.insert(subscription);
        return subscription;
    }

    private AgentDailyReportSubscriptionVO toSubscriptionVO(AgentDailyReportSubscription subscription) {
        AgentDailyReportSubscriptionVO vo = new AgentDailyReportSubscriptionVO();
        vo.setId(subscription.getId());
        vo.setUserId(subscription.getUserId());
        vo.setEnabled(subscription.getEnabled());
        vo.setSendTime(subscription.getSendTime());
        vo.setEmailEnabled(subscription.getEmailEnabled());
        vo.setLastGenerateDate(subscription.getLastGenerateDate());
        vo.setUpdateTime(subscription.getUpdateTime());
        return vo;
    }

    private Date normalizeDate(Date date) {
        Date source = date == null ? new Date() : date;
        LocalDate localDate = source.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer normalizeSwitch(Integer value, Integer defaultValue) {
        if (Integer.valueOf(0).equals(value)) {
            return 0;
        }
        if (Integer.valueOf(1).equals(value)) {
            return 1;
        }
        return defaultValue;
    }

    private String normalizeSendTime(String sendTime) {
        if (!StringUtils.hasText(sendTime)) {
            return DEFAULT_SEND_TIME;
        }
        try {
            return LocalTime.parse(sendTime.trim(), SEND_TIME_FORMATTER).format(SEND_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日报发送时间格式必须为 HH:mm");
        }
    }

    private boolean alreadyGeneratedToday(AgentDailyReportSubscription subscription, Date today) {
        return subscription.getLastGenerateDate() != null
                && normalizeDate(subscription.getLastGenerateDate()).equals(today);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String shortText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
