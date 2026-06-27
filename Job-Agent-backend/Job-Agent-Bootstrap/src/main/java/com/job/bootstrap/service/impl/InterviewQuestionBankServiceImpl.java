package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.interview.model.InterviewQuestionImportItem;
import com.job.bootstrap.interview.parser.InterviewQuestionMarkdownParser;
import com.job.bootstrap.mapper.InterviewQuestionBankMapper;
import com.job.bootstrap.rag.model.RagDocumentSource;
import com.job.bootstrap.rag.model.RagDocumentType;
import com.job.bootstrap.rag.model.RagTextChunk;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.rag.service.RagKnowledgeService;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.bootstrap.rag.utils.RagTextSplitter;
import com.job.bootstrap.service.InterviewQuestionBankService;
import com.job.common.dto.interview.InterviewQuestionBankQueryDTO;
import com.job.common.dto.interview.InterviewQuestionImportDTO;
import com.job.common.entity.interview.InterviewQuestionBank;
import com.job.common.entity.rag.RagChunk;
import com.job.common.vo.interview.InterviewQuestionBankVO;
import com.job.common.vo.interview.InterviewQuestionImportResultVO;
import com.job.common.vo.rag.RagIndexResultVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * AI 模拟面试题库后台服务实现。
 *
 * 核心链路:
 * 1. 从本地 markdown 目录读取题目文件。
 * 2. 解析出“题目 + 标准答案 + 分类”等结构化数据。
 * 3. 写入 interview_question_bank，作为 admin 可管理的题库主表。
 * 4. 同步写入 rag_document/rag_chunk 和 pgvector，让后续模拟面试可以按 RAG 抽题和对答案。
 */
@Service
@RequiredArgsConstructor
public class InterviewQuestionBankServiceImpl implements InterviewQuestionBankService {

    private static final String DEFAULT_DIRECTORY = "D:\\workspace\\job-mcp-docs";
    private static final long PUBLIC_USER_ID = 0L;
    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";

    private final InterviewQuestionBankMapper questionBankMapper;
    private final InterviewQuestionMarkdownParser markdownParser;
    private final RagTextSplitter ragTextSplitter;
    private final RagEmbeddingService ragEmbeddingService;
    private final RagVectorStoreService ragVectorStoreService;
    private final RagKnowledgeService ragKnowledgeService;

    /**
     * 从服务端本地目录导入 markdown 面试题。
     *
     * 方法步骤:
     * 1. 解析目录参数，没传则使用默认题库目录。
     * 2. 扫描目录下所有 .md 文件，逐个读取 UTF-8 文本。
     * 3. 使用 markdownParser 把标题块解析成题目和标准答案。
     * 4. 基于“题目 + 答案”计算 hash，存在则更新，不存在则插入。
     * 5. 如果 indexAfterImport=true，则导入后立即写入 RAG 和 pgvector。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewQuestionImportResultVO importLocalMarkdown(InterviewQuestionImportDTO dto) {
        Path directory = resolveDirectory(dto);
        if (!Files.isDirectory(directory)) {
            throw new BizException("面试题库目录不存在: " + directory);
        }

        InterviewQuestionImportResultVO result = new InterviewQuestionImportResultVO();
        boolean indexAfterImport = dto == null || dto.getIndexAfterImport() == null || dto.getIndexAfterImport();

        for (Path file : listMarkdownFiles(directory)) {
            result.addScannedFile();
            importSingleFile(file, indexAfterImport, result);
        }
        return result;
    }

    /**
     * 分页查询题库。
     *
     * 方法步骤:
     * 1. 对页码和页大小做兜底，防止后台传空值。
     * 2. 按 keyword/category/difficulty/status/sourceFile 组合查询。
     * 3. 只返回未删除数据，按更新时间倒序展示最新导入结果。
     */
    @Override
    public IPage<InterviewQuestionBankVO> pageQuestions(InterviewQuestionBankQueryDTO query) {
        InterviewQuestionBankQueryDTO safeQuery = query == null ? new InterviewQuestionBankQueryDTO() : query;
        LambdaQueryWrapper<InterviewQuestionBank> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewQuestionBank::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(safeQuery.getKeyword())) {
            String keyword = safeQuery.getKeyword().trim();
            wrapper.and(item -> item
                    .like(InterviewQuestionBank::getQuestionTitle, keyword)
                    .or()
                    .like(InterviewQuestionBank::getStandardAnswer, keyword)
                    .or()
                    .like(InterviewQuestionBank::getTags, keyword));
        }
        wrapper.eq(StringUtils.hasText(safeQuery.getQuestionType()), InterviewQuestionBank::getQuestionType, trim(safeQuery.getQuestionType()));
        wrapper.eq(StringUtils.hasText(safeQuery.getCategory()), InterviewQuestionBank::getCategory, trim(safeQuery.getCategory()));
        wrapper.eq(StringUtils.hasText(safeQuery.getDifficulty()), InterviewQuestionBank::getDifficulty, trim(safeQuery.getDifficulty()));
        wrapper.eq(StringUtils.hasText(safeQuery.getStatus()), InterviewQuestionBank::getStatus, trim(safeQuery.getStatus()));
        wrapper.like(StringUtils.hasText(safeQuery.getSourceFile()), InterviewQuestionBank::getSourceFile, trim(safeQuery.getSourceFile()));
        wrapper.orderByDesc(InterviewQuestionBank::getUpdateTime);

        Page<InterviewQuestionBank> page = new Page<>(
                normalizePageNum(safeQuery.getPageNum()),
                normalizePageSize(safeQuery.getPageSize())
        );
        return questionBankMapper.selectPage(page, wrapper).convert(InterviewQuestionBankVO::from);
    }

    @Override
    public InterviewQuestionBankVO getDetail(Long id) {
        return InterviewQuestionBankVO.from(loadQuestion(id));
    }

    /**
     * 启用或禁用题目。
     *
     * 第一版只管理题库状态，不物理删除 RAG 数据。
     * 后续模拟面试抽题会只抽 ACTIVE 状态，禁用题自然不会再进入新面试。
     */
    @Override
    public void updateStatus(Long id, String status) {
        if (!ACTIVE.equals(status) && !DISABLED.equals(status)) {
            throw new BizException("题库状态只支持 ACTIVE 或 DISABLED");
        }

        InterviewQuestionBank question = loadQuestion(id);
        question.setStatus(status);
        question.setUpdateTime(new Date());
        questionBankMapper.updateById(question);
    }

    /**
     * 重建单道题的 RAG 索引。
     *
     * 方法步骤:
     * 1. 查询题库主表，确认题目存在且未删除。
     * 2. 拼装适合 RAG 检索的完整文本，包含题目、分类、难度、标签和标准答案。
     * 3. 删除旧 pgvector 向量，避免同一题多次重建后被重复召回。
     * 4. 写入 rag_document/rag_chunk，得到 admin 可见的文档和切片。
     * 5. 对每个 chunk 生成 embedding 并写入 pgvector。
     * 6. 标记 RAG 文档为 INDEXED，并把 documentId/chunkId 回写到题库表。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagIndexResultVO indexQuestion(Long id) {
        InterviewQuestionBank question = loadQuestion(id);
        RagIndexResultVO result = new RagIndexResultVO();
        indexQuestion(question, result);
        return result;
    }

    /**
     * 重建全部启用题目的 RAG 索引。
     *
     * 第一版采用同步执行，适合管理员手工点击。
     * 如果后续题库很大，可以接入你前面做的工作流任务队列异步执行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagIndexResultVO indexAllActive() {
        RagIndexResultVO result = new RagIndexResultVO();
        List<InterviewQuestionBank> questions = questionBankMapper.selectList(new LambdaQueryWrapper<InterviewQuestionBank>()
                .eq(InterviewQuestionBank::getStatus, ACTIVE)
                .eq(InterviewQuestionBank::getIsDeleted, NOT_DELETED)
                .orderByAsc(InterviewQuestionBank::getId));

        for (InterviewQuestionBank question : questions) {
            indexQuestion(question, result);
        }
        return result;
    }

    private void importSingleFile(Path file, boolean indexAfterImport, InterviewQuestionImportResultVO result) {
        try {
            String markdown = Files.readString(file, StandardCharsets.UTF_8);
            List<InterviewQuestionImportItem> items = markdownParser.parse(file.getFileName().toString(), markdown);
            for (InterviewQuestionImportItem item : items) {
                result.addParsedQuestion();
                InterviewQuestionBank saved = saveImportItem(item, result);
                if (indexAfterImport) {
                    RagIndexResultVO indexResult = new RagIndexResultVO();
                    indexQuestion(saved, indexResult);
                    if (indexResult.getIndexedDocumentCount() > 0) {
                        result.addIndexed();
                    }
                    result.getWarnings().addAll(indexResult.getWarnings());
                }
            }
        } catch (Exception exception) {
            result.addFailed(file.getFileName() + " 导入失败: " + exception.getMessage());
        }
    }

    private InterviewQuestionBank saveImportItem(InterviewQuestionImportItem item, InterviewQuestionImportResultVO result) {
        String sourceHash = sha256(item.getQuestionTitle() + "\n" + item.getStandardAnswer());
        InterviewQuestionBank question = questionBankMapper.selectOne(new LambdaQueryWrapper<InterviewQuestionBank>()
                .eq(InterviewQuestionBank::getSourceHash, sourceHash)
                .last("LIMIT 1"));
        boolean insert = question == null;
        Date now = new Date();

        if (insert) {
            question = new InterviewQuestionBank();
            question.setSourceHash(sourceHash);
            question.setCreateTime(now);
            question.setIsDeleted(NOT_DELETED);
            question.setStatus(ACTIVE);
        }

        question.setQuestionTitle(item.getQuestionTitle());
        question.setStandardAnswer(item.getStandardAnswer());
        question.setQuestionType(defaultIfBlank(item.getQuestionType(), "TECHNICAL"));
        question.setCategory(item.getCategory());
        question.setDifficulty(defaultIfBlank(item.getDifficulty(), "MEDIUM"));
        question.setTags(item.getTags());
        question.setSourceFile(item.getSourceFile());
        question.setUpdateTime(now);
        question.setIsDeleted(NOT_DELETED);

        if (insert) {
            questionBankMapper.insert(question);
            result.addInserted();
        } else {
            questionBankMapper.updateById(question);
            result.addUpdated();
        }
        return question;
    }

    private void indexQuestion(InterviewQuestionBank question, RagIndexResultVO result) {
        if (question == null || !StringUtils.hasText(question.getStandardAnswer())) {
            result.addSkippedDocument();
            return;
        }

        RagDocumentSource document = toRagDocument(question);
        List<String> texts = ragTextSplitter.split(document.getContent());
        if (CollectionUtils.isEmpty(texts)) {
            result.addSkippedDocument();
            return;
        }

        try {
            ragVectorStoreService.ensureSchema();
            ragVectorStoreService.deleteDocument(PUBLIC_USER_ID, RagDocumentType.INTERVIEW_QUESTION.name(), question.getId());
            List<RagChunk> storedChunks = ragKnowledgeService.saveDocumentChunks(document, texts);
            List<RagTextChunk> vectorChunks = new ArrayList<>();
            for (RagChunk chunk : storedChunks) {
                vectorChunks.add(RagTextChunk.builder()
                        .userId(chunk.getUserId())
                        .documentType(RagDocumentType.INTERVIEW_QUESTION)
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
            ragKnowledgeService.markDocumentIndexed(PUBLIC_USER_ID, RagDocumentType.INTERVIEW_QUESTION.name(), question.getId());
            writeBackRagIds(question, storedChunks);
            result.addIndexedDocument(vectorChunks.size());
        } catch (Exception exception) {
            ragKnowledgeService.markDocumentIndexFailed(PUBLIC_USER_ID, RagDocumentType.INTERVIEW_QUESTION.name(), question.getId(), exception.getMessage());
            result.getWarnings().add("面试题 RAG 索引失败: questionId=" + question.getId() + ", error=" + exception.getMessage());
        }
    }

    private RagDocumentSource toRagDocument(InterviewQuestionBank question) {
        StringBuilder content = new StringBuilder();
        appendLine(content, "知识类型", "面试题");
        appendLine(content, "题目", question.getQuestionTitle());
        appendLine(content, "题目类型", question.getQuestionType());
        appendLine(content, "分类", question.getCategory());
        appendLine(content, "难度", question.getDifficulty());
        appendLine(content, "标签", question.getTags());
        appendLine(content, "标准答案", question.getStandardAnswer());

        return RagDocumentSource.builder()
                .userId(PUBLIC_USER_ID)
                .documentType(RagDocumentType.INTERVIEW_QUESTION)
                .businessId(question.getId())
                .title("面试题:" + question.getQuestionTitle())
                .source("interview_question_bank")
                .content(content.toString())
                .metadata(metadata(
                        "questionBankId", question.getId(),
                        "questionTitle", question.getQuestionTitle(),
                        "questionType", question.getQuestionType(),
                        "category", question.getCategory(),
                        "difficulty", question.getDifficulty(),
                        "tags", question.getTags(),
                        "sourceFile", question.getSourceFile()
                ))
                .build();
    }

    private void writeBackRagIds(InterviewQuestionBank question, List<RagChunk> storedChunks) {
        if (CollectionUtils.isEmpty(storedChunks)) {
            return;
        }

        RagChunk firstChunk = storedChunks.get(0);
        question.setRagDocumentId(firstChunk.getDocumentId());
        question.setRagChunkId(firstChunk.getId());
        question.setUpdateTime(new Date());
        questionBankMapper.updateById(question);
    }

    private InterviewQuestionBank loadQuestion(Long id) {
        if (id == null || id <= 0) {
            throw new BizException("题目 ID 不能为空");
        }

        InterviewQuestionBank question = questionBankMapper.selectById(id);
        if (question == null || Integer.valueOf(DELETED).equals(question.getIsDeleted())) {
            throw new BizException("面试题不存在");
        }
        return question;
    }

    private Path resolveDirectory(InterviewQuestionImportDTO dto) {
        String directoryPath = dto == null ? null : dto.getDirectoryPath();
        return Path.of(StringUtils.hasText(directoryPath) ? directoryPath.trim() : DEFAULT_DIRECTORY);
    }

    private List<Path> listMarkdownFiles(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new BizException("扫描面试题库目录失败: " + exception.getMessage());
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
        metadata.put("permissionScope", "PUBLIC");
        return metadata;
    }

    private Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value == null) {
                continue;
            }
            if (value instanceof String text && !StringUtils.hasText(text)) {
                continue;
            }
            metadata.put(String.valueOf(keyValues[i]), value);
        }
        return metadata;
    }

    private void appendLine(StringBuilder builder, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (StringUtils.hasText(text)) {
            builder.append(label).append(": ").append(text.trim()).append('\n');
        }
    }

    private long normalizePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : value;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("计算题目 hash 失败", exception);
        }
    }
}
