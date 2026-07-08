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
 * <p>核心职责：管理面试题库的生命周期，包括从本地 markdown 导入题目、分页查询、状态管理，以及将题目构建为 RAG 文档并写入向量数据库，支撑后续模拟面试的抽题与答案对照。</p>
 *
 * <p>所属业务模块：面试辅导模块（interview）/ 后台管理模块（admin）</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>管理员调用 {@link #importLocalMarkdown} 从本地 markdown 目录批量导入题目；</li>
 *   <li>题目经 {@link InterviewQuestionMarkdownParser} 解析后入库 interview_question_bank；</li>
 *   <li>管理员调用 {@link #indexQuestion} 或 {@link #indexAllActive} 重建 RAG 索引；</li>
 *   <li>模拟面试模块通过 {@link RagVectorStoreService} 检索已索引题目。</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link InterviewQuestionMarkdownParser} 解析 markdown 格式题目；</li>
 *   <li>依赖 {@link RagKnowledgeService} 管理 RAG 文档和切片元数据；</li>
 *   <li>依赖 {@link RagEmbeddingService} 生成文本 embedding；</li>
 *   <li>依赖 {@link RagVectorStoreService} 写入和删除 pgvector 向量数据；</li>
 *   <li>依赖 {@link InterviewQuestionBankMapper} 进行题库数据持久化。</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>题库主表与 RAG 索引解耦，导入和索引分两步执行，便于管理员审阅后再上线；</li>
 *   <li>基于题目+答案计算 SHA-256 哈希，实现幂等导入，重复内容自动更新而非重复插入；</li>
 *   <li>单道题和全量索引均支持重建，方便后续答案迭代后刷新向量库；</li>
 *   <li>第一版采用同步执行，适合管理员手工触发，后续题库量增大可接入工作流异步执行。</li>
 * </ol>
 * </p>
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

    /**
     * 查询单道面试题详情。
     *
     * @param id 题目主键
     * @return 题目详情 VO
     */
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

    /**
     * 导入单个 markdown 文件。
     *
     * <p>步骤：读取文件文本 -> 解析题目 -> 保存或更新题库记录 -> 按需立即建索引。</p>
     *
     * @param file             文件路径
     * @param indexAfterImport 导入后是否立即重建 RAG 索引
     * @param result           导入结果统计
     */
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

    /**
     * 保存或更新单道导入题目。
     *
     * <p>基于题目+答案的 SHA-256 哈希判断是新增还是更新，实现幂等导入。</p>
     *
     * @param item   解析后的导入项
     * @param result 导入结果统计
     * @return 保存后的题库实体
     */
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

    /**
     * 对单道题目执行 RAG 索引重建。
     *
     * <p>步骤：构建 RAG 文档 -> 文本切片 -> 删除旧向量 -> 生成 embedding -> 写入 pgvector -> 标记已索引 -> 回写 ID。</p>
     *
     * @param question 题库实体
     * @param result   索引结果统计
     */
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

    /**
     * 将题库实体转换为 RAG 文档源对象。
     *
     * <p>拼装包含题目、类型、分类、难度、标签和标准答案的完整文本，用于 embedding 和向量检索。</p>
     *
     * @param question 题库实体
     * @return RAG 文档源对象
     */
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

    /**
     * 将 RAG 文档 ID 和切片 ID 回写到题库主表，建立题库与 RAG 索引的关联。
     *
     * @param question     题库实体
     * @param storedChunks 已持久化的 RAG 切片列表
     */
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

    /**
     * 加载并校验题库实体。
     *
     * @param id 题目主键
     * @return 非空且未删除的题库实体
     * @throws BizException 题目不存在或 ID 非法时抛出
     */
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

    /**
     * 解析导入目录参数，未传则使用默认目录。
     *
     * @param dto 导入请求 DTO
     * @return 目录路径
     */
    private Path resolveDirectory(InterviewQuestionImportDTO dto) {
        String directoryPath = dto == null ? null : dto.getDirectoryPath();
        return Path.of(StringUtils.hasText(directoryPath) ? directoryPath.trim() : DEFAULT_DIRECTORY);
    }

    /**
     * 扫描目录下所有 markdown 文件并按文件名排序。
     *
     * @param directory 目录路径
     * @return markdown 文件路径列表
     * @throws BizException 扫描失败时抛出
     */
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

    /**
     * 构建切片级元数据，合并文档元数据和切片维度信息。
     *
     * @param document RAG 文档源对象
     * @param chunk    RAG 切片实体
     * @return 元数据 Map
     */
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

    /**
     * 构建键值对元数据，自动跳过 null 和空字符串值。
     *
     * @param keyValues 成对的键值参数
     * @return 元数据 Map
     */
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

    /**
     * 向 StringBuilder 追加一行 "label: value"，value 为空时跳过。
     *
     * @param builder 字符串构建器
     * @param label   标签
     * @param value   值
     */
    private void appendLine(StringBuilder builder, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (StringUtils.hasText(text)) {
            builder.append(label).append(": ").append(text.trim()).append('\n');
        }
    }

    /**
     * 规范化页码，非法值返回默认值。
     *
     * @param pageNum 原始页码
     * @return 安全页码
     */
    private long normalizePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 规范化页大小，非法值返回默认值，超过上限则截断。
     *
     * @param pageSize 原始页大小
     * @return 安全页大小
     */
    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 字符串空值兜底，无有效内容时返回默认值。
     *
     * @param value        原始字符串
     * @param defaultValue 默认值
     * @return 有效字符串或默认值
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 字符串去首尾空白，null 原样返回。
     *
     * @param value 原始字符串
     * @return 去空白后的字符串或 null
     */
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : value;
    }

    /**
     * 计算文本的 SHA-256 哈希值。
     *
     * @param text 原始文本
     * @return 十六进制哈希字符串
     * @throws IllegalStateException 算法初始化失败时抛出
     */
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
