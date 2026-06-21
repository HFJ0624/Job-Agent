package com.job.bootstrap.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.JobCommunicationMessageMapper;
import com.job.bootstrap.mapper.JobCommunicationRecordMapper;
import com.job.bootstrap.rag.model.RagDocumentSource;
import com.job.bootstrap.rag.model.RagDocumentType;
import com.job.bootstrap.rag.model.RagTextChunk;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.rag.service.RagIndexService;
import com.job.bootstrap.rag.service.RagKnowledgeService;
import com.job.bootstrap.rag.utils.RagTextSplitter;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.bootstrap.service.JobCompanyService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.communication.JobCommunicationMessage;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.rag.RagChunk;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.rag.RagIndexResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作者:hfj
 * 功能:RAG 知识库索引服务实现
 * 日期:2026/6/14
 */
@Service
@RequiredArgsConstructor
public class RagIndexServiceImpl implements RagIndexService {

    private static final long PUBLIC_USER_ID = 0L;
    private static final int NOT_DELETED = 0;
    private static final int ENABLED_STATUS = 1;

    private final JobResumeService jobResumeService;
    private final JobPositionService jobPositionService;
    private final JobCompanyService jobCompanyService;
    private final JobCommunicationRecordMapper jobCommunicationRecordMapper;
    private final JobCommunicationMessageMapper jobCommunicationMessageMapper;
    private final RagTextSplitter ragTextSplitter;
    private final RagEmbeddingService ragEmbeddingService;
    private final RagVectorStoreService ragVectorStoreService;
    private final RagKnowledgeService ragKnowledgeService;

    /**
     * 重建当前用户可用的全部知识。
     *
     * @param userId 当前登录用户 ID
     * @return 索引结果
     */
    @Override
    public RagIndexResultVO rebuildAllKnowledge(Long userId) {
        validateUserId(userId);

        RagIndexResultVO result = new RagIndexResultVO();
        ragVectorStoreService.ensureSchema();

        /*
         * 1. 公共知识: 岗位 JD 和公司信息，使用 user_id=0。
         * 2. 私有知识: 当前用户的简历和沟通记录，使用真实 userId。
         * 3. 检索时同时查 user_id=0 和当前 userId，既能利用公共资料，又不会串用户数据。
         */
        rebuildPublicKnowledge(result);
        rebuildUserKnowledge(userId, result);
        return result;
    }

    /**
     * 只重建公共知识。
     *
     * @return 索引结果
     */
    @Override
    public RagIndexResultVO rebuildPublicKnowledge() {
        RagIndexResultVO result = new RagIndexResultVO();
        ragVectorStoreService.ensureSchema();
        rebuildPublicKnowledge(result);
        return result;
    }

    /**
     * 重建所有用户可用的全部知识。
     * 该方法面向后台管理员，用于统一刷新公共知识和所有用户私有知识。
     *
     * @return 索引结果
     */
    @Override
    public RagIndexResultVO rebuildAllUserKnowledge() {
        RagIndexResultVO result = new RagIndexResultVO();
        ragVectorStoreService.ensureSchema();

        /*
         * 1. 先重建公共知识，确保岗位和公司信息是最新的。
         * 2. 再收集所有出现过简历或沟通记录的用户 ID。
         * 3. 逐个用户重建私有知识，避免不同用户的简历和沟通记录混在一起。
         */
        rebuildPublicKnowledge(result);
        for (Long userId : listKnowledgeUserIds()) {
            rebuildUserKnowledge(userId, result);
        }

        return result;
    }

    /**
     * 只重建当前用户私有知识。
     *
     * @param userId 当前登录用户 ID
     * @return 索引结果
     */
    @Override
    public RagIndexResultVO rebuildUserKnowledge(Long userId) {
        validateUserId(userId);

        RagIndexResultVO result = new RagIndexResultVO();
        ragVectorStoreService.ensureSchema();
        rebuildUserKnowledge(userId, result);
        return result;
    }

    /**
     * 增量索引单个业务文档。
     *
     * 方法步骤:
     * 1. 根据 documentType 和 businessId 读取来源业务数据。
     * 2. 删除同一文档旧的 pgvector 向量记录。
     * 3. 重新切块并写入 MySQL 可视化表。
     * 4. 重新生成 embedding 并写入 pgvector。
     */
    @Override
    public RagIndexResultVO indexDocument(Long userId, String documentType, Long businessId) {
        RagIndexResultVO result = new RagIndexResultVO();
        ragVectorStoreService.ensureSchema();

        RagDocumentSource document = loadDocumentSource(userId, documentType, businessId);
        ragVectorStoreService.deleteDocument(
                document.getUserId(),
                document.getDocumentType().name(),
                document.getBusinessId()
        );
        indexDocument(document, result);
        return result;
    }

    /**
     * 同步删除单个业务文档的 RAG 索引。
     *
     * 说明:
     * 1. MySQL 可视化层标记删除。
     * 2. pgvector 向量层物理删除，避免继续召回旧知识。
     */
    @Override
    public RagIndexResultVO deleteDocument(Long userId, String documentType, Long businessId) {
        RagDocumentType type = parseDocumentType(documentType);
        Long actualUserId = resolveIndexUserId(userId, type);

        ragKnowledgeService.markDocumentDeleted(actualUserId, type.name(), businessId);
        ragVectorStoreService.deleteDocument(actualUserId, type.name(), businessId);

        RagIndexResultVO result = new RagIndexResultVO();
        result.getWarnings().add("已同步删除 RAG 文档: userId=" + actualUserId
                + ", documentType=" + type.name()
                + ", businessId=" + businessId);
        return result;
    }

    /***
     *  重建公共知识
     * @param result 返回rag响应对象
     */
    private void rebuildPublicKnowledge(RagIndexResultVO result) {
        ragKnowledgeService.markDocumentsDeleted(
                PUBLIC_USER_ID,
                List.of(RagDocumentType.JOB.name(), RagDocumentType.COMPANY.name())
        );
        ragVectorStoreService.deleteDocuments(
                PUBLIC_USER_ID,
                List.of(RagDocumentType.JOB.name(), RagDocumentType.COMPANY.name())
        );

        List<JobCompany> companies = listEnabledCompanies();
        Map<Long, JobCompany> companyMap = companies.stream()
                .filter(company -> company.getId() != null)
                .collect(Collectors.toMap(JobCompany::getId, Function.identity(), (oldValue, newValue) -> oldValue));

        List<JobPosition> positions = listPublishedPositions();
        for (JobPosition position : positions) {
            JobCompany company = companyMap.get(position.getCompanyId());
            indexDocument(toJobDocument(position, company), result);
        }

        for (JobCompany company : companies) {
            indexDocument(toCompanyDocument(company), result);
        }
    }

    private void rebuildUserKnowledge(Long userId, RagIndexResultVO result) {
        ragKnowledgeService.markDocumentsDeleted(
                userId,
                List.of(
                        RagDocumentType.RESUME.name(),
                        RagDocumentType.COMMUNICATION.name(),
                        RagDocumentType.COMMUNICATION_MESSAGE.name()
                )
        );
        ragVectorStoreService.deleteDocuments(
                userId,
                List.of(
                        RagDocumentType.RESUME.name(),
                        RagDocumentType.COMMUNICATION.name(),
                        RagDocumentType.COMMUNICATION_MESSAGE.name()
                )
        );

        List<JobResume> resumes = jobResumeService.listUserResumes(userId);
        for (JobResume resume : resumes) {
            indexDocument(toResumeDocument(resume), result);
        }

        List<JobCommunicationRecord> records = listUserCommunicationRecords(userId);
        Map<Long, JobPosition> positionMap = loadPositionMap(records);
        Map<Long, JobCompany> companyMap = loadCompanyMap(positionMap.values());

        for (JobCommunicationRecord record : records) {
            JobPosition position = positionMap.get(record.getJobId());
            JobCompany company = position == null ? null : companyMap.get(position.getCompanyId());
            indexDocument(toCommunicationDocument(record, position, company), result);
        }

        List<JobCommunicationMessage> messages = listUserCommunicationMessages(userId);
        for (JobCommunicationMessage message : messages) {
            indexDocument(toCommunicationMessageDocument(message), result);
        }
    }

    private void indexDocument(RagDocumentSource document, RagIndexResultVO result) {
        if (document == null || !StringUtils.hasText(document.getContent())) {
            result.addSkippedDocument();
            return;
        }

        List<String> texts = ragTextSplitter.split(document.getContent());
        if (CollectionUtils.isEmpty(texts)) {
            result.addSkippedDocument();
            return;
        }

        /*
         * 1. 一个业务文档会被切成多个 chunk。
         * 2. 每个 chunk 单独生成 embedding，便于后续精确召回最相关段落。
         * 3. businessId + chunkIndex 保证同一业务文档可以被幂等更新。
         */
        try {
            /*
             * 1. 先把文档和切块写入 MySQL，拿到 documentId/chunkId。
             * 2. 再把 chunkId 写进 pgvector metadata，后续命中时可以回到主库展示引用。
             * 3. 向量写入成功后回写 INDEXED 状态，admin 页面才能看到索引是否成功。
             */
            List<RagChunk> storedChunks = ragKnowledgeService.saveDocumentChunks(document, texts);
            List<RagTextChunk> vectorChunks = new ArrayList<>();
            for (RagChunk chunk : storedChunks) {
                vectorChunks.add(RagTextChunk.builder()
                        .userId(chunk.getUserId())
                        .documentType(document.getDocumentType())
                        .businessId(chunk.getBusinessId())
                        .chunkIndex(chunk.getChunkIndex())
                        .title(chunk.getTitle())
                        .content(chunk.getContent())
                        .source(chunk.getSource())
                        .metadata(buildChunkMetadata(document, chunk))
                        .embedding(ragEmbeddingService.embed(chunk.getContent()))
                        .contentHash(chunk.getContentHash())
                        .build());
            }

            ragVectorStoreService.saveChunks(vectorChunks);
            ragKnowledgeService.markDocumentIndexed(
                    document.getUserId(),
                    document.getDocumentType().name(),
                    document.getBusinessId()
            );
            result.addIndexedDocument(vectorChunks.size());
            increaseTypeCount(result, document.getDocumentType());
        } catch (Exception exception) {
            ragKnowledgeService.markDocumentIndexFailed(
                    document.getUserId(),
                    document.getDocumentType().name(),
                    document.getBusinessId(),
                    exception.getMessage()
            );
            result.getWarnings().add("RAG 文档索引失败: "
                    + document.getDocumentType().name()
                    + "#"
                    + document.getBusinessId()
                    + ", error="
                    + exception.getMessage());
        }
    }

    private Map<String, Object> buildChunkMetadata(RagDocumentSource document, RagChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (document.getMetadata() != null) {
            metadata.putAll(document.getMetadata());
        }
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkId", chunk.getId());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("permissionScope", chunk.getUserId() != null && chunk.getUserId().equals(PUBLIC_USER_ID)
                ? "PUBLIC"
                : "PRIVATE");
        return metadata;
    }

    /***
     *
     * @param userId 用户id
     * @param documentType 文档类型
     * @param businessId 来源业务id
     * @return 加载文档来源
     */
    private RagDocumentSource loadDocumentSource(Long userId, String documentType, Long businessId) {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId 不能为空");
        }

        RagDocumentType type = parseDocumentType(documentType);
        Long actualUserId = resolveIndexUserId(userId, type);

        return switch (type) {
            case JOB -> {
                JobPosition position = jobPositionService.getPositionRequired(businessId);
                JobCompany company = position.getCompanyId() == null
                        ? null
                        : jobCompanyService.getCompanyRequired(position.getCompanyId());
                yield toJobDocument(position, company);
            }
            case COMPANY -> toCompanyDocument(jobCompanyService.getCompanyRequired(businessId));
            case RESUME -> toResumeDocument(jobResumeService.getUserResumeRequired(actualUserId, businessId));
            case COMMUNICATION -> {
                JobCommunicationRecord record = loadUserCommunicationRecord(actualUserId, businessId);
                JobPosition position = record.getJobId() == null ? null : jobPositionService.getById(record.getJobId());
                JobCompany company = position == null || position.getCompanyId() == null
                        ? null
                        : jobCompanyService.getById(position.getCompanyId());
                yield toCommunicationDocument(record, position, company);
            }
            case COMMUNICATION_MESSAGE -> toCommunicationMessageDocument(loadUserCommunicationMessage(actualUserId, businessId));
        };
    }

    private RagDocumentType parseDocumentType(String documentType) {
        if (!StringUtils.hasText(documentType)) {
            throw new IllegalArgumentException("documentType 不能为空");
        }
        try {
            return RagDocumentType.valueOf(documentType.trim().toUpperCase());
        } catch (Exception exception) {
            throw new IllegalArgumentException("不支持的 RAG 文档类型: " + documentType, exception);
        }
    }

    private Long resolveIndexUserId(Long userId, RagDocumentType type) {
        if (type == RagDocumentType.JOB || type == RagDocumentType.COMPANY) {
            return PUBLIC_USER_ID;
        }
        validateUserId(userId);
        return userId;
    }

    private JobCommunicationRecord loadUserCommunicationRecord(Long userId, Long businessId) {
        JobCommunicationRecord record = jobCommunicationRecordMapper.selectOne(
                new LambdaQueryWrapper<JobCommunicationRecord>()
                        .eq(JobCommunicationRecord::getId, businessId)
                        .eq(JobCommunicationRecord::getUserId, userId)
                        .eq(JobCommunicationRecord::getIsDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (record == null) {
            throw new IllegalArgumentException("沟通记录不存在或无权索引: " + businessId);
        }
        return record;
    }

    private JobCommunicationMessage loadUserCommunicationMessage(Long userId, Long businessId) {
        JobCommunicationMessage message = jobCommunicationMessageMapper.selectOne(
                new LambdaQueryWrapper<JobCommunicationMessage>()
                        .eq(JobCommunicationMessage::getId, businessId)
                        .eq(JobCommunicationMessage::getUserId, userId)
                        .eq(JobCommunicationMessage::getIsDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (message == null) {
            throw new IllegalArgumentException("沟通消息不存在或无权索引: " + businessId);
        }
        return message;
    }

    private RagDocumentSource toResumeDocument(JobResume resume) {
        StringBuilder content = new StringBuilder();
        appendLine(content, "知识类型", "简历");
        appendLine(content, "简历名称", resume.getResumeName());
        appendLine(content, "文件名", resume.getFileName());
        appendLine(content, "文件类型", resume.getFileType());
        appendLine(content, "默认简历", resume.getIsDefault());
        appendLine(content, "简历状态", resume.getStatus());
        appendLine(content, "简历评分", resume.getScore());
        appendLine(content, "原始解析文本", resume.getRawText());
        appendLine(content, "结构化简历JSON", resume.getParsedJson());

        return RagDocumentSource.builder()
                .userId(resume.getUserId())
                .documentType(RagDocumentType.RESUME)
                .businessId(resume.getId())
                .title("简历:" + defaultIfBlank(resume.getResumeName(), resume.getFileName()))
                .source("resume")
                .content(content.toString())
                .metadata(metadata(
                        "resumeId", resume.getId(),
                        "resumeName", resume.getResumeName(),
                        "fileName", resume.getFileName(),
                        "fileType", resume.getFileType(),
                        "status", resume.getStatus(),
                        "isDefault", resume.getIsDefault()
                ))
                .build();
    }

    private RagDocumentSource toJobDocument(JobPosition position, JobCompany company) {
        String companyName = company == null ? null : company.getCompanyName();

        StringBuilder content = new StringBuilder();
        appendLine(content, "知识类型", "岗位JD");
        appendLine(content, "岗位名称", position.getJobTitle());
        appendLine(content, "公司名称", companyName);
        appendLine(content, "岗位类别", position.getJobCategory());
        appendLine(content, "工作城市", joinLocation(position.getCity(), position.getDistrict()));
        appendLine(content, "薪资范围", salaryText(position));
        appendLine(content, "学历要求", position.getEducationReq());
        appendLine(content, "经验要求", position.getExperienceReq());
        appendLine(content, "工作类型", position.getWorkType());
        appendLine(content, "技能关键词", position.getSkillKeywords());
        appendLine(content, "福利标签", position.getWelfareTags());
        appendLine(content, "岗位描述", position.getJobDescription());
        appendLine(content, "任职要求", position.getJobRequirement());
        appendLine(content, "来源链接", position.getSourceUrl());

        return RagDocumentSource.builder()
                .userId(PUBLIC_USER_ID)
                .documentType(RagDocumentType.JOB)
                .businessId(position.getId())
                .title(defaultIfBlank(position.getJobTitle(), "岗位") + " @ " + defaultIfBlank(companyName, "未知公司"))
                .source("job_position")
                .content(content.toString())
                .metadata(metadata(
                        "jobId", position.getId(),
                        "companyId", position.getCompanyId(),
                        "companyName", companyName,
                        "jobTitle", position.getJobTitle(),
                        "city", position.getCity(),
                        "district", position.getDistrict(),
                        "educationReq", position.getEducationReq(),
                        "experienceReq", position.getExperienceReq(),
                        "skillKeywords", position.getSkillKeywords()
                ))
                .build();
    }

    private RagDocumentSource toCompanyDocument(JobCompany company) {
        StringBuilder content = new StringBuilder();
        appendLine(content, "知识类型", "公司信息");
        appendLine(content, "公司名称", company.getCompanyName());
        appendLine(content, "所属行业", company.getIndustry());
        appendLine(content, "公司规模", company.getCompanySize());
        appendLine(content, "融资阶段", company.getFinancingStage());
        appendLine(content, "发展前景评分", company.getProspectScore());
        appendLine(content, "所在城市", joinLocation(company.getProvince(), company.getCity(), company.getDistrict()));
        appendLine(content, "详细地址", company.getAddress());
        appendLine(content, "公司简介", company.getDescription());

        return RagDocumentSource.builder()
                .userId(PUBLIC_USER_ID)
                .documentType(RagDocumentType.COMPANY)
                .businessId(company.getId())
                .title("公司:" + company.getCompanyName())
                .source("company")
                .content(content.toString())
                .metadata(metadata(
                        "companyId", company.getId(),
                        "companyName", company.getCompanyName(),
                        "industry", company.getIndustry(),
                        "companySize", company.getCompanySize(),
                        "financingStage", company.getFinancingStage(),
                        "city", company.getCity()
                ))
                .build();
    }

    private RagDocumentSource toCommunicationDocument(
            JobCommunicationRecord record,
            JobPosition position,
            JobCompany company
    ) {
        String jobTitle = position == null ? null : position.getJobTitle();
        String companyName = company == null ? null : company.getCompanyName();

        StringBuilder content = new StringBuilder();
        appendLine(content, "知识类型", "求职沟通记录");
        appendLine(content, "沟通平台", record.getPlatform());
        appendLine(content, "岗位名称", jobTitle);
        appendLine(content, "公司名称", companyName);
        appendLine(content, "外部岗位链接", record.getExternalJobUrl());
        appendLine(content, "HR名称", record.getHrName());
        appendLine(content, "HR联系方式", record.getHrContact());
        appendLine(content, "沟通状态", record.getCommunicationStatus());
        appendLine(content, "发送给HR的开场白", record.getGreetingText());
        appendLine(content, "HR回复", record.getHrReply());
        appendLine(content, "AI建议回复", record.getAiReplyText());
        appendLine(content, "用户最终回复", record.getUserReplyText());
        appendLine(content, "备注", record.getNote());
        appendLine(content, "面试时间", record.getInterviewTime());
        appendLine(content, "面试方式", record.getInterviewMethod());
        appendLine(content, "面试地点", record.getInterviewLocation());
        appendLine(content, "面试平台", record.getInterviewPlatform());
        appendLine(content, "会议链接", record.getMeetingLink());
        appendLine(content, "面试联系人", record.getInterviewContact());
        appendLine(content, "AI提取面试邀约JSON", record.getInterviewExtractJson());

        return RagDocumentSource.builder()
                .userId(record.getUserId())
                .documentType(RagDocumentType.COMMUNICATION)
                .businessId(record.getId())
                .title("沟通记录:" + defaultIfBlank(companyName, "未知公司") + "/" + defaultIfBlank(jobTitle, "未知岗位"))
                .source("job_communication_record")
                .content(content.toString())
                .metadata(metadata(
                        "communicationId", record.getId(),
                        "applicationId", record.getApplicationId(),
                        "resumeId", record.getResumeId(),
                        "jobId", record.getJobId(),
                        "jobTitle", jobTitle,
                        "companyName", companyName,
                        "platform", record.getPlatform(),
                        "communicationStatus", record.getCommunicationStatus()
                ))
                .build();
    }

    private RagDocumentSource toCommunicationMessageDocument(JobCommunicationMessage message) {
        StringBuilder content = new StringBuilder();
        appendLine(content, "知识类型", "求职沟通消息流水");
        appendLine(content, "沟通记录ID", message.getCommunicationId());
        appendLine(content, "消息发送方", message.getSenderType());
        appendLine(content, "回复风格", message.getReplyStyle());
        appendLine(content, "保存后状态", message.getStatusAfter());
        appendLine(content, "消息内容", message.getMessageContent());

        return RagDocumentSource.builder()
                .userId(message.getUserId())
                .documentType(RagDocumentType.COMMUNICATION_MESSAGE)
                .businessId(message.getId())
                .title("沟通消息:" + message.getSenderType() + "#" + message.getId())
                .source("job_communication_message")
                .content(content.toString())
                .metadata(metadata(
                        "messageId", message.getId(),
                        "communicationId", message.getCommunicationId(),
                        "senderType", message.getSenderType(),
                        "replyStyle", message.getReplyStyle(),
                        "statusAfter", message.getStatusAfter()
                ))
                .build();
    }

    private List<JobPosition> listPublishedPositions() {
        return jobPositionService.list(new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getIsDeleted, NOT_DELETED)
                .eq(JobPosition::getStatus, ENABLED_STATUS));
    }

    private List<JobCompany> listEnabledCompanies() {
        return jobCompanyService.list(new LambdaQueryWrapper<JobCompany>()
                .eq(JobCompany::getIsDeleted, NOT_DELETED)
                .eq(JobCompany::getStatus, ENABLED_STATUS));
    }

    private List<JobCommunicationRecord> listUserCommunicationRecords(Long userId) {
        return jobCommunicationRecordMapper.selectList(new LambdaQueryWrapper<JobCommunicationRecord>()
                .eq(JobCommunicationRecord::getUserId, userId)
                .eq(JobCommunicationRecord::getIsDeleted, NOT_DELETED));
    }

    private List<JobCommunicationMessage> listUserCommunicationMessages(Long userId) {
        return jobCommunicationMessageMapper.selectList(new LambdaQueryWrapper<JobCommunicationMessage>()
                .eq(JobCommunicationMessage::getUserId, userId)
                .eq(JobCommunicationMessage::getIsDeleted, NOT_DELETED));
    }

    private Map<Long, JobPosition> loadPositionMap(List<JobCommunicationRecord> records) {
        List<Long> jobIds = records.stream()
                .map(JobCommunicationRecord::getJobId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(jobIds)) {
            return Map.of();
        }

        return jobPositionService.listByIds(jobIds).stream()
                .filter(position -> position.getId() != null)
                .collect(Collectors.toMap(JobPosition::getId, Function.identity(), (oldValue, newValue) -> oldValue));
    }

    private Map<Long, JobCompany> loadCompanyMap(Collection<JobPosition> positions) {
        if (CollectionUtils.isEmpty(positions)) {
            return Map.of();
        }

        List<Long> companyIds = positions.stream()
                .map(JobPosition::getCompanyId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(companyIds)) {
            return Map.of();
        }

        return jobCompanyService.listByIds(companyIds).stream()
                .filter(company -> company.getId() != null)
                .collect(Collectors.toMap(JobCompany::getId, Function.identity(), (oldValue, newValue) -> oldValue));
    }

    private List<Long> listKnowledgeUserIds() {
        Set<Long> userIds = new LinkedHashSet<>();

        jobResumeService.list(new LambdaQueryWrapper<JobResume>()
                        .select(JobResume::getUserId)
                        .eq(JobResume::getIsDeleted, NOT_DELETED))
                .forEach(resume -> addUserId(userIds, resume.getUserId()));

        jobCommunicationRecordMapper.selectList(new LambdaQueryWrapper<JobCommunicationRecord>()
                        .select(JobCommunicationRecord::getUserId)
                        .eq(JobCommunicationRecord::getIsDeleted, NOT_DELETED))
                .forEach(record -> addUserId(userIds, record.getUserId()));

        jobCommunicationMessageMapper.selectList(new LambdaQueryWrapper<JobCommunicationMessage>()
                        .select(JobCommunicationMessage::getUserId)
                        .eq(JobCommunicationMessage::getIsDeleted, NOT_DELETED))
                .forEach(message -> addUserId(userIds, message.getUserId()));

        return new ArrayList<>(userIds);
    }

    private void addUserId(Set<Long> userIds, Long userId) {
        if (userId != null && userId > 0) {
            userIds.add(userId);
        }
    }

    private void increaseTypeCount(RagIndexResultVO result, RagDocumentType type) {
        switch (type) {
            case RESUME -> result.setResumeCount(result.getResumeCount() + 1);
            case JOB -> result.setJobCount(result.getJobCount() + 1);
            case COMPANY -> result.setCompanyCount(result.getCompanyCount() + 1);
            case COMMUNICATION -> result.setCommunicationCount(result.getCommunicationCount() + 1);
            case COMMUNICATION_MESSAGE -> result.setMessageCount(result.getMessageCount() + 1);
        }
    }

    private void appendLine(StringBuilder builder, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = value instanceof Date ? value.toString() : String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return;
        }

        builder.append(label).append(": ").append(text.trim()).append('\n');
    }

    private String salaryText(JobPosition position) {
        if (position.getMinSalary() == null && position.getMaxSalary() == null) {
            return null;
        }
        String min = position.getMinSalary() == null ? "未知" : String.valueOf(position.getMinSalary());
        String max = position.getMaxSalary() == null ? "未知" : String.valueOf(position.getMaxSalary());
        String months = position.getSalaryMonths() == null ? "" : "，" + position.getSalaryMonths() + "薪";
        return min + "-" + max + "元/月" + months;
    }

    private String joinLocation(String... parts) {
        return Arrays.stream(parts)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value == null) {
                continue;
            }
            if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
                continue;
            }
            if (value instanceof BigDecimal bigDecimal) {
                metadata.put(String.valueOf(keyValues[i]), bigDecimal.toPlainString());
            } else {
                metadata.put(String.valueOf(keyValues[i]), value);
            }
        }
        return metadata;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须是正数");
        }
    }
}
