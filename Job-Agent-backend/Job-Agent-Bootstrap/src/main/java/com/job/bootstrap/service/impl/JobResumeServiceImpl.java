package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.job.bootstrap.config.MinioProperties;
import com.job.bootstrap.mapper.JobResumeMapper;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.resume.JobResume;
import com.job.exception.BizException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 简历业务服务实现类。
 *
 * <p>核心职责：负责简历（JobResume）全生命周期管理，包括文件上传、MinIO 对象存储、
 * 简历列表查询、名称修改、逻辑删除、默认简历设置以及基于 Apache Tika 的文本解析。
 * 提供文件类型校验、内容抽取质量检测与乱码识别能力。</p>
 *
 * <p>所属业务模块：用户简历模块（Resume Management）</p>
 *
 * <p>主要调用链：
 * <pre>
 * JobResumeController -&gt; JobResumeService -&gt; JobResumeServiceImpl
 *                                |
 *                                v
 *                    JobResumeMapper / MinioClient / Tika
 * </pre></p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>继承 {@link ServiceImpl}，依赖 {@link JobResumeMapper} 进行简历元数据持久化</li>
 *   <li>通过 {@link MinioClient} 与 {@link MinioProperties} 对接 MinIO 对象存储服务</li>
 *   <li>使用 {@link Tika} 进行 PDF、DOC、DOCX 等格式的文本抽取</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>所有写操作均使用 {@link Transactional} 保证事务一致性</li>
 *   <li>文件上传采用“先存 MinIO，再写数据库”的顺序，失败时通过异常回滚</li>
 *   <li>默认简历唯一：同一用户仅允许一份默认简历，删除默认简历后自动降级最新简历</li>
 *   <li>文本解析后执行空文本、乱码、控制字符等多维度质量检测，确保 rawText 可用性</li>
 *   <li>rawText 长度上限 60,000 字符，超长自动截断，避免数据库和前端性能问题</li>
 * </ul></p>
 *
 * @author hfj
 * @since 2026/6/4
 */
@Service
@RequiredArgsConstructor
public class JobResumeServiceImpl extends ServiceImpl<JobResumeMapper, JobResume> implements JobResumeService {

    /**
     * 简历最大文件大小，和 application-dev.yml 中 multipart.max-file-size 保持一致。
     */
    private static final long RESUME_MAX_SIZE = 10 * 1024 * 1024L;

    /**
     * 简历上传后最初的状态。
     */
    private static final String STATUS_UPLOADED = "UPLOADED";

    /**
     * 简历解析中状态。
     */
    private static final String STATUS_PARSING = "PARSING";

    /**
     * 简历解析成功状态。
     */
    private static final String STATUS_PARSED = "PARSED";

    /**
     * 简历解析失败状态。
     */
    private static final String STATUS_PARSE_FAILED = "PARSE_FAILED";

    /**
     * raw_text 最多保存的字符数，避免一次把超大文档内容塞进页面和数据库。
     */
    private static final int RAW_TEXT_MAX_LENGTH = 60_000;

    /**
     * Word 文档里的图片会以 image1.png、word/media/image2.jpeg 这种内部资源名出现。
     * P表示参数描述，这类内容不是简历正文，保存 raw_text 前直接过滤掉。
     */
    private static final Pattern EMBEDDED_IMAGE_LINE_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:[\\w.-]+[/\\\\])*image\\d+\\.(?:png|jpe?g|gif|bmp|webp|tiff?|svg|emf|wmf)\\s*$\\R?"
    );

    /**
     * 逻辑未删除状态。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 不是默认简历。
     */
    private static final int NOT_DEFAULT = 0;

    /**
     * 默认简历标记。
     */
    private static final int DEFAULT_RESUME = 1;

    /**
     * 逻辑已删除状态。
     */
    private static final int DELETED = 1;

    /**
     * 允许上传的简历文件扩展名。
     */
    private static final Set<String> RESUME_EXTENSIONS = Set.of(".pdf", ".doc", ".docx");

    private final MinioClient minioClient;

    private final MinioProperties minioProperties;

    /**
     * Apache Tika 文本抽取入口。
     * P表示参数描述：Tika 会根据文件内容自动选择 PDFBox、POI 等解析器。
     */
    private final Tika tika = new Tika();

    /**
     * 上传简历。
     *
     * <p>校验文件大小、格式与简历名称唯一性后，将文件上传至 MinIO 的 resume 目录，
     * 再把文件元数据（URL、大小、类型等）写入数据库。同一用户的简历名称不可重复。</p>
     *
     * @param userId 当前登录用户 ID
     * @param resumeName 简历名称
     * @param file 前端上传的简历文件
     * @return 返回保存后的简历实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResume uploadResume(Long userId, String resumeName, MultipartFile file) {
        String cleanedResumeName = trimToNull(resumeName);
        String originalFilename = file == null ? null : normalizeOriginalFilename(file.getOriginalFilename());

        try {
            // 1. 先校验基础参数，避免空文件、超大文件、非简历格式文件进入后续流程。
            validateResumeFile(cleanedResumeName, file);

            // 2. 同一个用户下简历名称不能重复，防止前端列表里出现两个同名简历。
            if (existsSameResumeName(userId, cleanedResumeName, null)) {
                throw new BizException("简历名称已经存在，请换一个名称");
            }

            // 3. 确保存储桶存在，不存在时自动创建，方便本地开发环境第一次启动。
            ensureBucketExists();

            // 4. 生成 resume 专用目录下的对象名，并把文件流上传到 MinIO。
            String extension = getExtension(originalFilename);
            String objectName = buildResumeObjectName(userId, extension);
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectName)
                        .contentType(resolveContentType(file))
                        .stream(inputStream, file.getSize(), -1)
                        .build());
            }

            // 5. MinIO 上传成功后，再把文件元数据保存到 resume 表。
            Date now = new Date();
            JobResume resume = new JobResume();
            resume.setUserId(userId);
            resume.setResumeName(cleanedResumeName);
            resume.setFileUrl(buildFileUrl(objectName));
            resume.setFileName(originalFilename);
            resume.setFileType(extension.substring(1).toUpperCase());
            resume.setFileSize(file.getSize());
            resume.setStatus(STATUS_UPLOADED);
            resume.setIsDefault(hasUserResume(userId) ? NOT_DEFAULT : DEFAULT_RESUME);
            resume.setIsDeleted(NOT_DELETED);
            resume.setCreateTime(now);
            resume.setUpdateTime(now);

            save(resume);
            return resume;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            // 6. 系统异常要打印到后台控制台，便于快速定位 MinIO、数据库或文件流问题。
            printResumeUploadError(userId, cleanedResumeName, originalFilename, exception);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "简历上传失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 查询当前用户的简历列表。
     *
     * <p>只查询当前用户未删除的简历，按默认标记优先、创建时间倒序展示。</p>
     *
     * @param userId 当前登录用户 ID
     * @return 返回该用户未删除的简历列表
     */
    @Override
    public List<JobResume> listUserResumes(Long userId) {
        // 1. 只查询当前用户未删除的简历，并按上传时间倒序展示。
        return list(new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getIsDeleted, NOT_DELETED)
                .orderByDesc(JobResume::getIsDefault)
                .orderByDesc(JobResume::getCreateTime));
    }

    /**
     * 修改简历名称。
     *
     * <p>先校验简历归属（防止越权修改），再判断新名称是否与其它简历重复；
     * 名称未变更时直接返回当前记录，避免无意义更新。</p>
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @param resumeName 新的简历名称
     * @return 返回修改后的简历实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResume updateResumeName(Long userId, Long resumeId, String resumeName) {
        // 1. 先查出当前用户自己的简历，防止用户通过改 ID 修改别人的简历。
        JobResume resume = getUserResumeRequired(userId, resumeId);

        // 2. 清洗名称并做基础校验；和上传接口保持同样的长度限制。
        String cleanedResumeName = trimToNull(resumeName);
        if (!StringUtils.hasText(cleanedResumeName)) {
            throw new BizException("简历名称不能为空");
        }
        if (cleanedResumeName.length() > 128) {
            throw new BizException("简历名称长度不能超过128位");
        }

        // 3. 如果名称没有变化，直接返回当前记录，避免无意义更新。
        if (cleanedResumeName.equals(resume.getResumeName())) {
            return resume;
        }

        // 4. 同一个用户下不能出现两个未删除的同名简历。
        if (existsSameResumeName(userId, cleanedResumeName, resumeId)) {
            throw new BizException("简历名称已经存在，请换一个名称");
        }

        // 5. 只修改简历展示名称和更新时间，文件地址、文件名等元数据不动。
        resume.setResumeName(cleanedResumeName);
        resume.setUpdateTime(new Date());
        updateById(resume);
        return getUserResumeRequired(userId, resumeId);
    }

    /**
     * 逻辑删除简历。
     *
     * <p>将简历标记为已删除并清除默认标记；若删除的是默认简历且用户还有其它简历，
     * 则自动将最新一份设为默认，保证默认简历不会悬空。</p>
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResume(Long userId, Long resumeId) {
        // 1. 先校验这份简历属于当前用户，避免越权删除。
        JobResume resume = getUserResumeRequired(userId, resumeId);

        // 2. 先记住它原来是不是默认简历，后面逻辑删除后可能需要补一个新的默认简历。
        boolean wasDefaultResume = resume.getIsDefault() != null && resume.getIsDefault() == DEFAULT_RESUME;

        // 3. 逻辑删除记录，并清掉默认标记，避免已删除简历仍然被当作默认简历。
        resume.setIsDeleted(DELETED);
        resume.setIsDefault(NOT_DEFAULT);
        resume.setUpdateTime(new Date());
        updateById(resume);

        // 4. 如果删除的是默认简历，并且用户还有其它简历，就自动把最新一份设为默认。
        if (wasDefaultResume) {
            chooseLatestResumeAsDefault(userId);
        }
    }

    /**
     * 设置默认简历。
     *
     * <p>将指定简历设为默认，并取消该用户下其它简历的默认标记，保证默认简历唯一性。</p>
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @return 返回设置后的默认简历实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResume setDefaultResume(Long userId, Long resumeId) {
        // 1. 先校验目标简历存在且属于当前用户。
        JobResume resume = getUserResumeRequired(userId, resumeId);

        // 2. 取消当前用户其它简历的默认标记，保证默认简历唯一。
        List<JobResume> userResumes = listUserResumes(userId);
        Date now = new Date();
        userResumes.forEach(item -> {
            item.setIsDefault(NOT_DEFAULT);
            item.setUpdateTime(now);
        });
        updateBatchById(userResumes);

        // 3. 再把目标简历设为默认简历。
        resume.setIsDefault(DEFAULT_RESUME);
        resume.setUpdateTime(now);
        updateById(resume);
        return getUserResumeRequired(userId, resumeId);
    }

    /**
     * 解析简历文本。
     *
     * <p>通过 Apache Tika 从 MinIO 读取文件并抽取文本，随后进行空文本、乱码、控制字符等
     * 质量检测；解析结果写入 rawText 字段，状态同步更新为解析成功或解析失败。</p>
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @return 返回解析后的简历实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResume parseResumeText(Long userId, Long resumeId) {
        // 1. 先校验简历归属，确保用户只能解析自己的简历。
        JobResume resume = getUserResumeRequired(userId, resumeId);

        // 2. 先把状态改成解析中，方便前端立刻显示“解析中”。
        resume.setStatus(STATUS_PARSING);
        resume.setUpdateTime(new Date());
        updateById(resume);

        try (InputStream inputStream = openResumeFile(resume)) {
            // 3. Tika 会按文件类型自动调用 PDFBox、POI 等解析器抽取文本。
            String extractedText = tika.parseToString(inputStream);

            // 4. 清洗文本并判断是否为空、乱码或明显不可读。
            ParseCheckResult checkResult = checkExtractedText(extractedText, resume);
            resume.setRawText(checkResult.text());
            resume.setStatus(checkResult.success() ? STATUS_PARSED : STATUS_PARSE_FAILED);
            resume.setUpdateTime(new Date());
            updateById(resume);
        } catch (Exception exception) {
            // 5. 解析异常也写入 rawText，前端可以直接把失败原因展示出来。
            printResumeParseError(resume, exception);
            resume.setRawText(buildParseFailedText(
                    "解析失败：文件读取或文本抽取过程出现异常。",
                    "可能原因：文件损坏、文件被密码保护、MinIO 文件不存在，或当前文件格式不被解析器支持。",
                    exception.getClass().getSimpleName() + "：" + exception.getMessage()
            ));
            resume.setStatus(STATUS_PARSE_FAILED);
            resume.setUpdateTime(new Date());
            updateById(resume);
        }

        // 6. 重新查询一次数据库，保证返回给前端的是最新状态和 rawText。
        return getUserResumeRequired(userId, resumeId);
    }

    /**
     * 查询当前用户的指定简历。
     *
     * <p>查询条件固定带上 userId，防止用户通过篡改 ID 读取他人简历；
     * 若简历不存在或已被逻辑删除，则抛出 {@link BizException}。</p>
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @return 返回简历实体，不存在或不属于当前用户时抛出业务异常
     */
    @Override
    public JobResume getUserResumeRequired(Long userId, Long resumeId) {
        // 1. 查询条件固定带上 userId，避免用户通过改 URL 读取别人上传的简历。
        JobResume resume = getOne(new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getId, resumeId)
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getIsDeleted, NOT_DELETED), false);
        if (resume == null) {
            throw new BizException("简历不存在");
        }
        return resume;
    }

    /**
     * 从 MinIO 打开简历文件流。
     *
     * <p>根据数据库保存的文件地址解析出 MinIO 对象名，再调用 MinIO 获取文件输入流。</p>
     *
     * @param resume 数据库中的简历实体
     * @return 返回简历文件输入流，由 Controller 交给浏览器读取
     */
    @Override
    public InputStream openResumeFile(JobResume resume) {
        try {
            // 1. 数据库保存的是完整文件地址，这里解析出 MinIO 真正需要的 objectName。
            String objectName = resolveObjectName(resume.getFileUrl());
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build());
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            printResumeFileError(resume, exception);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "简历文件读取失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 校验简历文件。
     *
     * @param resumeName 简历名称
     * @param file 前端上传的文件
     */
    private void validateResumeFile(String resumeName, MultipartFile file) {
        if (!StringUtils.hasText(resumeName)) {
            throw new BizException("简历名称不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的简历文件");
        }
        if (file.getSize() > RESUME_MAX_SIZE) {
            throw new BizException("简历文件不能超过10MB");
        }

        String extension = getExtension(normalizeOriginalFilename(file.getOriginalFilename()));
        if (!RESUME_EXTENSIONS.contains(extension)) {
            throw new BizException("简历只支持 PDF、DOC、DOCX 格式");
        }
    }

    /**
     * 判断同一个用户下是否已经存在同名简历。
     *
     * @param userId 当前登录用户 ID
     * @param resumeName 简历名称
     * @return true 表示已存在，false 表示不存在
     */
    private boolean existsSameResumeName(Long userId, String resumeName, Long excludeResumeId) {
        LambdaQueryWrapper<JobResume> wrapper = new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getResumeName, resumeName)
                .eq(JobResume::getIsDeleted, NOT_DELETED);

        // 1. 修改名称时要排除自己，否则名称不变也会被误判为重复。
        if (excludeResumeId != null) {
            wrapper.ne(JobResume::getId, excludeResumeId);
        }
        return count(wrapper) > 0;
    }

    /**
     * 判断用户是否已经有未删除简历。
     *
     * @param userId 当前登录用户 ID
     * @return true 表示已有简历，false 表示还没有简历
     */
    private boolean hasUserResume(Long userId) {
        return count(new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getIsDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 把当前用户最新上传的一份简历设为默认简历。
     * P表示参数描述，删除默认简历后使用这个方法补一个默认值。
     *
     * @param userId 当前登录用户 ID
     */
    private void chooseLatestResumeAsDefault(Long userId) {
        JobResume latestResume = getOne(new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getIsDeleted, NOT_DELETED)
                .orderByDesc(JobResume::getCreateTime)
                .last("limit 1"), false);

        if (latestResume != null) {
            latestResume.setIsDefault(DEFAULT_RESUME);
            latestResume.setUpdateTime(new Date());
            updateById(latestResume);
        }
    }

    /**
     * 检查抽取文本是否可用。
     * P表示参数描述：空文本通常说明是扫描件图片，乱码通常说明原文件编码或解析器识别失败。
     *
     * @param extractedText Tika 抽取出的原始文本
     * @param resume 当前简历实体
     * @return 返回检查后的结果
     */
    private ParseCheckResult checkExtractedText(String extractedText, JobResume resume) {
        String cleanedText = normalizeExtractedText(extractedText);
        if (!StringUtils.hasText(cleanedText)) {
            return ParseCheckResult.failed(buildParseFailedText(
                    "解析失败：没有提取到有效文本。",
                    "可能原因：这份简历可能是扫描件图片型 PDF、文件被加密、文件内容为空，或 Word/PDF 文件本身已损坏。",
                    "建议：请上传可复制文字的 PDF、DOC 或 DOCX 文件。"
            ));
        }

        String visibleText = cleanedText.replaceAll("\\s+", "");
        int visibleLength = visibleText.length();
        int replacementCharCount = countChar(visibleText, '\uFFFD');
        int controlCharCount = countControlCharacters(visibleText);
        int mojibakeSignalCount = countMojibakeSignals(visibleText);

        // 1. 出现大量 �、控制字符或典型 UTF-8/GBK 错解片段时，基本可以判断为乱码。
        boolean hasManyReplacementChars = visibleLength > 0 && replacementCharCount * 100.0 / visibleLength > 1.0;
        boolean hasManyControlChars = visibleLength > 0 && controlCharCount * 100.0 / visibleLength > 2.0;
        boolean hasMojibakeSignals = mojibakeSignalCount >= 8;

        if (hasManyReplacementChars || hasManyControlChars || hasMojibakeSignals) {
            return ParseCheckResult.failed(buildParseFailedText(
                    "解析失败：检测到乱码。",
                    "可能原因：原文件编码异常、文件从其它格式强行改后缀、Word 文档内部结构损坏，或 PDF 使用了不标准字体映射。",
                    "检测信息：替换字符数量=" + replacementCharCount
                            + "，异常控制字符数量=" + controlCharCount
                            + "，疑似乱码片段数量=" + mojibakeSignalCount
                            + "，文件类型=" + resume.getFileType()
                            + "。\n抽取片段：\n" + abbreviate(cleanedText, 800)
            ));
        }

        return ParseCheckResult.success(abbreviate(cleanedText, RAW_TEXT_MAX_LENGTH));
    }

    /**
     * 清洗解析文本。
     *
     * @param text 原始解析文本
     * @return 返回适合保存和展示的文本
     */
    private String normalizeExtractedText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        // 1. 统一换行，去掉首尾空白，避免前端展示时出现大面积空白。
        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ')
                .trim();

        // 2. DOCX 内嵌图片会被解析成 image1.png 这种单独一行的资源名，不属于简历正文。
        normalized = EMBEDDED_IMAGE_LINE_PATTERN.matcher(normalized).replaceAll("");

        // 3. 多个空行压成最多两个空行，保留简历段落结构。
        return normalized.replaceAll("\\n{3,}", "\n\n").trim();
    }

    /**
     * 构造解析失败时写入 rawText 的文本。
     *
     * @param title 失败标题
     * @param reason 失败原因
     * @param detail 失败细节
     * @return 返回前端可直接展示的失败说明
     */
    private String buildParseFailedText(String title, String reason, String detail) {
        return title + "\n" + reason + "\n" + detail;
    }

    /**
     * 统计指定字符出现次数。
     *
     * @param text 文本
     * @param target 目标字符
     * @return 返回出现次数
     */
    private int countChar(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计异常控制字符数量。
     *
     * @param text 文本
     * @return 返回异常控制字符数量
     */
    private int countControlCharacters(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t') {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计疑似乱码片段数量。
     * P表示参数描述：中文被错误按 Latin-1 显示时，常出现 Ã、Â、å、æ、ç 等连续片段。
     *
     * @param text 文本
     * @return 返回疑似乱码信号数量
     */
    private int countMojibakeSignals(String text) {
        String[] signals = {"Ã", "Â", "å", "æ", "ç", "è", "é", "¤", "¥"};
        int count = 0;
        for (String signal : signals) {
            int index = text.indexOf(signal);
            while (index >= 0) {
                count++;
                index = text.indexOf(signal, index + signal.length());
            }
        }
        return count;
    }

    /**
     * 截断过长文本。
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 返回截断后的文本
     */
    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n\n……内容过长，已截断展示。";
    }

    /**
     * 确保 MinIO 存储桶存在。
     */
    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioProperties.getBucketName())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .build());
        }
    }

    /**
     * 生成简历文件在 MinIO 中的对象名。
     *
     * @param userId 当前登录用户 ID
     * @param extension 文件扩展名
     * @return 返回 resume 目录下的对象名
     */
    private String buildResumeObjectName(Long userId, String extension) {
        LocalDate now = LocalDate.now();
        return "resume/" + userId + "/" + now.getYear() + "/" + now.getMonthValue() + "/" + UUID.randomUUID() + extension;
    }

    /**
     * 构造文件访问地址。
     *
     * @param objectName MinIO 对象名
     * @return 返回可保存到数据库的完整访问 URL
     */
    private String buildFileUrl(String objectName) {
        String endpoint = minioProperties.getEndpointUrl();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioProperties.getBucketName() + "/" + objectName;
    }

    /**
     * 从数据库文件地址中解析 MinIO 对象名。
     *
     * @param fileUrl 数据库保存的文件地址
     * @return 返回 MinIO 对象名，例如 resume/1/2026/6/xxx.pdf
     */
    private String resolveObjectName(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BizException("简历文件地址为空");
        }

        String endpoint = minioProperties.getEndpointUrl();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        String urlPrefix = endpoint + "/" + minioProperties.getBucketName() + "/";
        if (fileUrl.startsWith(urlPrefix)) {
            return fileUrl.substring(urlPrefix.length());
        }

        // 1. 兼容以后如果只保存 objectName 的情况。
        if (fileUrl.startsWith("resume/")) {
            return fileUrl;
        }
        throw new BizException("简历文件地址格式不正确");
    }

    /**
     * 获取文件扩展名。
     *
     * @param originalFilename 原始文件名
     * @return 返回小写扩展名，例如 .pdf；没有扩展名时返回空字符串
     */
    private String getExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * 获取安全的原始文件名。
     *
     * @param originalFilename 浏览器上传的原始文件名
     * @return 返回去掉路径信息后的文件名
     */
    private String normalizeOriginalFilename(String originalFilename) {
        String filename = StringUtils.getFilename(originalFilename);
        if (!StringUtils.hasText(filename)) {
            return "resume";
        }
        return filename;
    }

    /**
     * 获取文件 Content-Type。
     *
     * @param file 前端上传的文件
     * @return 返回文件 Content-Type，拿不到时使用二进制兜底类型
     */
    private String resolveContentType(MultipartFile file) {
        if (!StringUtils.hasText(file.getContentType())) {
            return "application/octet-stream";
        }
        return file.getContentType();
    }

    /**
     * 字符串清洗工具。
     *
     * @param value 原始字符串
     * @return 去掉首尾空格后的字符串；如果没有有效内容则返回 null
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 打印简历上传失败的详细诊断信息。
     *
     * @param userId 当前登录用户 ID
     * @param resumeName 简历名称
     * @param originalFilename 原始文件名
     * @param exception 原始异常
     */
    private void printResumeUploadError(Long userId, String resumeName, String originalFilename, Exception exception) {
        System.err.println();
        System.err.println("========== Job-Agent 简历上传异常 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobResumeServiceImpl.uploadResume");
        System.err.println("用户ID：" + userId);
        System.err.println("简历名称：" + resumeName);
        System.err.println("原始文件名：" + originalFilename);
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("异常信息：" + exception.getMessage());
        exception.printStackTrace(System.err);
        System.err.println("==========================================");
        System.err.println();
    }

    /**
     * 打印简历文件读取失败的详细诊断信息。
     *
     * @param resume 简历实体
     * @param exception 原始异常
     */
    private void printResumeFileError(JobResume resume, Exception exception) {
        System.err.println();
        System.err.println("========== Job-Agent 简历文件读取异常 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobResumeServiceImpl.openResumeFile");
        System.err.println("简历ID：" + resume.getId());
        System.err.println("用户ID：" + resume.getUserId());
        System.err.println("简历名称：" + resume.getResumeName());
        System.err.println("文件地址：" + resume.getFileUrl());
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("异常信息：" + exception.getMessage());
        exception.printStackTrace(System.err);
        System.err.println("==========================================");
        System.err.println();
    }

    /**
     * 打印简历解析失败的详细诊断信息。
     *
     * @param resume 简历实体
     * @param exception 原始异常
     */
    private void printResumeParseError(JobResume resume, Exception exception) {
        System.err.println();
        System.err.println("========== Job-Agent 简历文本解析异常 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobResumeServiceImpl.parseResumeText");
        System.err.println("简历ID：" + resume.getId());
        System.err.println("用户ID：" + resume.getUserId());
        System.err.println("简历名称：" + resume.getResumeName());
        System.err.println("文件地址：" + resume.getFileUrl());
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("异常信息：" + exception.getMessage());
        exception.printStackTrace(System.err);
        System.err.println("==========================================");
        System.err.println();
    }

    /**
     * 简历解析检查结果。
     * P表示参数描述，success 表示 rawText 是否为有效解析内容，text 表示最终保存到数据库的文本。
     */
    private record ParseCheckResult(boolean success, String text) {

        private static ParseCheckResult success(String text) {
            return new ParseCheckResult(true, text);
        }

        private static ParseCheckResult failed(String text) {
            return new ParseCheckResult(false, text);
        }
    }
}
