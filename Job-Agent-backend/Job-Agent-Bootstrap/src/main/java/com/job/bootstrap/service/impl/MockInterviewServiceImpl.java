package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.MockInterviewAnswerMapper;
import com.job.bootstrap.mapper.MockInterviewMediaRecordMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.mapper.MockInterviewWrongQuestionMapper;
import com.job.bootstrap.service.FileStorageService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.bootstrap.service.InterviewQuestionSelectorService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.MockInterviewService;
import com.job.bootstrap.service.MockInterviewWrongQuestionService;
import com.job.bootstrap.service.SpeechRecognitionService;
import com.job.common.dto.interview.AiInterviewStartDTO;
import com.job.common.dto.interview.MockInterviewAnswerDTO;
import com.job.common.dto.interview.MockInterviewStartDTO;
import com.job.common.enums.MockInterviewErrorCode;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.interview.InterviewQuestionBank;
import com.job.common.entity.interview.MockInterviewAnswer;
import com.job.common.entity.interview.MockInterviewMediaRecord;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.interview.MockInterviewSession;
import com.job.common.entity.interview.MockInterviewWrongQuestion;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.interview.InterviewPrepareVO;
import com.job.common.vo.interview.MockInterviewAnswerVO;
import com.job.common.vo.interview.MockInterviewMediaRecordVO;
import com.job.common.vo.interview.MockInterviewQuestionVO;
import com.job.common.vo.interview.MockInterviewSessionVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 模拟面试核心服务实现。
 *
 * <p>核心职责：管理模拟面试全生命周期，包括会话启动、题目推送、文本/语音答题、AI 评分、会话结束及错题自动收录。
 *
 * <p>所属业务模块：面试训练中心 - 模拟面试（Mock Interview）。
 *
 * <p>主要调用链：
 * <ul>
 *   <li>用户端：{@code startSession / startAiInterview} → {@code getCurrentQuestion} →
 *       {@code submitAnswer / submitAudioAnswer} → {@code finishSession}</li>
 *   <li>评分链路：{@code createAnswerAndAdvance} → {@code evaluateAnswer} →
 *       {@code evaluateAnswerWithStandardAnswer}（模型优先）→ 规则兜底 → {@code saveWrongQuestionIfNeeded}</li>
 * </ul>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>{@link InterviewPrepareService}：旧流程依赖面试准备记录生成题目种子。</li>
 *   <li>{@link InterviewQuestionSelectorService}：新流程通过 RAG + 题库召回匹配题目。</li>
 *   <li>{@link AiModelGatewayService}：统一模型网关，负责答题评分和复盘 Prompt 调用。</li>
 *   <li>{@link SpeechRecognitionService} + {@link FileStorageService}：语音答题链路（ASR + 文件存储）。</li>
 *   <li>{@link MockInterviewWrongQuestionService}：读取薄弱知识点，用于个性化出题。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>兼容双入口：保留基于求职记录的旧流程，同时新增“简历 + 岗位”直接启动的 AI 面试流程。</li>
 *   <li>评分双保险：优先调用 LLM 做语义评分，模型不可用时自动降级为规则评分，避免单点故障。</li>
 *   <li>语音答题复用文本链路：先存原始音频和 ASR 结果，再用 ASR 文本走统一评分逻辑，保证数据一致性。</li>
 *   <li>错题自动沉淀：每题评分后按得分、判错标志、缺失要点综合决策是否进入错题本，支撑后续学习计划和复测。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MockInterviewServiceImpl implements MockInterviewService {

    private static final int NOT_DELETED = 0;
    private static final int PUBLISHED = 1;

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_FINISHED = "FINISHED";

    private static final String TYPE_TECHNICAL = "TECHNICAL";
    private static final String TYPE_PROJECT = "PROJECT";
    private static final String TYPE_HR = "HR";

    private static final String MEDIA_TYPE_AUDIO = "AUDIO";
    private static final String ASR_PENDING = "PENDING";
    private static final String ASR_SUCCESS = "SUCCESS";
    private static final String ASR_FAILED = "FAILED";
    private static final String AI_SCENE_MOCK_INTERVIEW_ANSWER_EVALUATE = "MOCK_INTERVIEW_ANSWER_EVALUATE";

    private final MockInterviewSessionMapper sessionMapper;
    private final MockInterviewQuestionMapper questionMapper;
    private final MockInterviewAnswerMapper answerMapper;
    private final MockInterviewMediaRecordMapper mediaRecordMapper;
    private final MockInterviewWrongQuestionMapper wrongQuestionMapper;
    private final JobApplicationRecordMapper applicationMapper;
    private final InterviewPrepareService interviewPrepareService;
    private final InterviewQuestionSelectorService interviewQuestionSelectorService;
    private final JobResumeService jobResumeService;
    private final JobPositionService jobPositionService;
    private final FileStorageService fileStorageService;
    private final SpeechRecognitionService speechRecognitionService;
    private final MockInterviewWrongQuestionService wrongQuestionService;
    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;

    /**
     * 基于求职记录启动模拟面试会话（旧流程兼容入口）。
     *
     * <p>步骤：
     * <ol>
     *   <li>校验求职记录归属。</li>
     *   <li>复用或自动生成面试准备记录。</li>
     *   <li>按准备记录中的技术/项目/HR 题构建题目种子。</li>
     *   <li>创建会话及题目，返回会话详情。</li>
     * </ol>
     *
     * @param userId 用户 ID
     * @param dto    启动参数，包含 applicationId、resumeId、questionCount
     * @return 新建会话的完整详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewSessionVO startSession(Long userId, MockInterviewStartDTO dto) {
        JobApplicationRecord application = applicationMapper.selectById(dto.getApplicationId());
        if (application == null || !userId.equals(application.getUserId())) {
            throw mockInterviewException(MockInterviewErrorCode.SESSION_NOT_FOUND, "求职记录不存在或无权限访问");
        }

        // 1. 旧流程优先复用面试准备记录；没有时自动生成一份，保证原页面不受影响。
        InterviewPrepareVO prepare = interviewPrepareService.getLatestPrepare(userId, dto.getApplicationId());
        if (prepare == null) {
            prepare = interviewPrepareService.generatePrepare(userId, dto.getApplicationId(), dto.getResumeId());
        }

        int questionCount = normalizeQuestionCount(dto.getQuestionCount());
        List<QuestionSeed> seeds = buildQuestionSeeds(prepare, questionCount);

        MockInterviewSession session = new MockInterviewSession();
        session.setUserId(userId);
        session.setApplicationId(application.getId());
        session.setInterviewPrepareId(prepare.getId());
        session.setJobId(application.getJobId());
        session.setResumeId(dto.getResumeId() != null ? dto.getResumeId() : application.getResumeId());
        session.setJobTitle(application.getJobTitle());
        session.setCompanyName(application.getCompanyName());
        createSessionAndQuestions(session, seeds);
        return getSessionDetail(userId, session.getId());
    }

    /**
     * 基于“简历 + 岗位”直接启动 AI 模拟面试会话（新流程入口）。
     *
     * <p>步骤：
     * <ol>
     *   <li>校验简历归属并解析文本。</li>
     *   <li>校验岗位状态（仅允许已发布岗位）。</li>
     *   <li>通过题库 + RAG 召回题目，不足时由规则题兜底。</li>
     *   <li>创建会话及题目，返回会话详情。</li>
     * </ol>
     *
     * @param userId 用户 ID
     * @param dto    启动参数，包含 resumeId、jobId、questionCount、excludeRecentHours
     * @return 新建会话的完整详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewSessionVO startAiInterview(Long userId, AiInterviewStartDTO dto) {
        // 1. 校验简历必须属于当前用户，避免用户通过篡改 resumeId 读取别人简历内容。
        JobResume resume = jobResumeService.getUserResumeRequired(userId, dto.getResumeId());
        if (!StringUtils.hasText(resume.getRawText())) {
            resume = jobResumeService.parseResumeText(userId, dto.getResumeId());
        }

        // 2. 岗位必须存在且未删除；用户端练习只允许面向已发布岗位。
        JobPosition job = jobPositionService.getPositionRequired(dto.getJobId());
        if (job.getStatus() == null || job.getStatus() != PUBLISHED) {
            throw mockInterviewException(MockInterviewErrorCode.JOB_NOT_AVAILABLE, "岗位未发布，不能用于 AI 面试");
        }

        // 3. 根据岗位 JD、技能关键词和简历文本生成第一版问题，不依赖用户先创建求职记录。
        int questionCount = normalizeQuestionCount(dto.getQuestionCount());
        List<String> weakKeywords = wrongQuestionService.listActiveWeakKnowledgePoints(userId, 8);
        List<QuestionSeed> seeds = buildQuestionSeedsFromBank(
                interviewQuestionSelectorService.selectQuestions(
                        userId,
                        job,
                        resume,
                        questionCount,
                        dto.getExcludeRecentHours(),
                        weakKeywords
                )
        );
        if (seeds.size() < questionCount) {
            seeds.addAll(buildAiQuestionSeeds(job, resume, questionCount));
        }
        seeds = limitDistinctSeeds(seeds, questionCount);

        MockInterviewSession session = new MockInterviewSession();
        session.setUserId(userId);
        session.setApplicationId(null);
        session.setInterviewPrepareId(null);
        session.setJobId(job.getId());
        session.setResumeId(resume.getId());
        session.setJobTitle(job.getJobTitle());
        session.setCompanyName(null);
        createSessionAndQuestions(session, seeds);
        return getSessionDetail(userId, session.getId());
    }

    /**
     * 获取模拟面试会话完整详情。
     *
     * <p>聚合会话、题目、回答、音频媒体记录，按时间/排序号组织后返回。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 会话详情 VO
     */
    @Override
    public MockInterviewSessionVO getSessionDetail(Long userId, Long sessionId) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);

        List<MockInterviewQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<MockInterviewQuestion>()
                .eq(MockInterviewQuestion::getSessionId, sessionId)
                .eq(MockInterviewQuestion::getUserId, userId)
                .eq(MockInterviewQuestion::getIsDeleted, NOT_DELETED)
                .orderByAsc(MockInterviewQuestion::getSortNo));

        List<MockInterviewAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<MockInterviewAnswer>()
                .eq(MockInterviewAnswer::getSessionId, sessionId)
                .eq(MockInterviewAnswer::getUserId, userId)
                .eq(MockInterviewAnswer::getIsDeleted, NOT_DELETED)
                .orderByAsc(MockInterviewAnswer::getCreateTime));

        List<MockInterviewMediaRecord> mediaRecords = mediaRecordMapper.selectList(new LambdaQueryWrapper<MockInterviewMediaRecord>()
                .eq(MockInterviewMediaRecord::getSessionId, sessionId)
                .eq(MockInterviewMediaRecord::getUserId, userId)
                .eq(MockInterviewMediaRecord::getIsDeleted, NOT_DELETED)
                .orderByAsc(MockInterviewMediaRecord::getCreateTime));

        MockInterviewSessionVO vo = MockInterviewSessionVO.from(session);
        vo.setQuestions(questions.stream().map(MockInterviewQuestionVO::from).toList());
        vo.setAnswers(answers.stream().map(MockInterviewAnswerVO::from).toList());
        vo.setMediaRecords(mediaRecords.stream().map(MockInterviewMediaRecordVO::from).toList());
        return vo;
    }

    /**
     * 获取当前会话下一道待回答题目。
     *
     * <p>若会话已结束，返回 {@code null}。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 下一题 VO，或会话结束时的 {@code null}
     */
    @Override
    public MockInterviewQuestionVO getCurrentQuestion(Long userId, Long sessionId) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);
        if (STATUS_FINISHED.equals(session.getStatus())) {
            return null;
        }

        MockInterviewQuestion question = questionMapper.selectOne(new LambdaQueryWrapper<MockInterviewQuestion>()
                .eq(MockInterviewQuestion::getSessionId, sessionId)
                .eq(MockInterviewQuestion::getUserId, userId)
                .eq(MockInterviewQuestion::getAnswered, 0)
                .eq(MockInterviewQuestion::getIsDeleted, NOT_DELETED)
                .orderByAsc(MockInterviewQuestion::getSortNo)
                .last("limit 1"));
        return MockInterviewQuestionVO.from(question);
    }

    /**
     * 提交文本回答并评分。
     *
     * <p>调用链：校验会话与题目 → 创建回答 → AI 评分 → 推进会话进度 → 自动结束（最后一题）。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param dto       回答内容
     * @return 评分后的回答 VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewAnswerVO submitAnswer(Long userId, Long sessionId, MockInterviewAnswerDTO dto) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);
        MockInterviewQuestion question = getQuestionForAnswer(userId, sessionId, dto.getQuestionId(), session);
        MockInterviewAnswer answer = createAnswerAndAdvance(session, question, dto.getAnswerContent());
        return MockInterviewAnswerVO.from(answer);
    }

    /**
     * 提交语音回答并评分。
     *
     * <p>步骤：
     * <ol>
     *   <li>保存原始音频文件到对象存储，生成媒体记录。</li>
     *   <li>调用 ASR 将语音转文本。</li>
     *   <li>ASR 成功后复用文本答题评分链路，并回填 answerId 到媒体记录。</li>
     * </ol>
     *
     * @param userId          用户 ID
     * @param sessionId       会话 ID
     * @param questionId      题目 ID
     * @param audio           音频文件
     * @param durationSeconds 音频时长（秒）
     * @return 评分后的回答 VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewAnswerVO submitAudioAnswer(Long userId, Long sessionId, Long questionId, MultipartFile audio, Integer durationSeconds) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);
        MockInterviewQuestion question = getQuestionForAnswer(userId, sessionId, questionId, session);

        // 1. 先保存原始音频，即使 ASR 失败，后台也能看到用户确实提交过音频。
        FileStorageService.StoredFile storedFile = fileStorageService.upload(audio, "mock-interview/audio");
        MockInterviewMediaRecord mediaRecord = new MockInterviewMediaRecord();
        mediaRecord.setSessionId(sessionId);
        mediaRecord.setQuestionId(questionId);
        mediaRecord.setUserId(userId);
        mediaRecord.setMediaType(MEDIA_TYPE_AUDIO);
        mediaRecord.setFileUrl(storedFile.fileUrl());
        mediaRecord.setObjectName(storedFile.objectName());
        mediaRecord.setFileName(storedFile.originalFilename());
        mediaRecord.setFileSize(storedFile.fileSize());
        mediaRecord.setDurationSeconds(durationSeconds);
        mediaRecord.setAsrProvider("VOLCENGINE");
        mediaRecord.setAsrStatus(ASR_PENDING);
        mediaRecord.setIsDeleted(NOT_DELETED);
        mediaRecordMapper.insert(mediaRecord);

        try {
            // 2. 调用 ASR，把语音转成文本；业务评分只吃文本，便于复用现有能力。
            SpeechRecognitionService.SpeechRecognitionResult asrResult = speechRecognitionService.recognize(
                    audio.getBytes(),
                    audio.getContentType(),
                    audio.getOriginalFilename()
            );

            if (!asrResult.success() || !StringUtils.hasText(asrResult.text())) {
                mediaRecord.setAsrStatus(ASR_FAILED);
                mediaRecord.setAsrError(asrResult.errorMessage());
                mediaRecordMapper.updateById(mediaRecord);
                throw mockInterviewException(
                        MockInterviewErrorCode.ASR_FAILED,
                        "语音识别失败: " + defaultIfBlank(asrResult.errorMessage(), "未识别到有效语音")
                );
            }

            // 3. ASR 成功后复用文本答题评分链路，并把 answerId 回填到媒体记录。
            mediaRecord.setAsrProvider(asrResult.provider());
            mediaRecord.setAsrText(asrResult.text());
            mediaRecord.setAsrStatus(ASR_SUCCESS);
            MockInterviewAnswer answer = createAnswerAndAdvance(session, question, asrResult.text());
            mediaRecord.setAnswerId(answer.getId());
            mediaRecordMapper.updateById(mediaRecord);
            return MockInterviewAnswerVO.from(answer);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            mediaRecord.setAsrStatus(ASR_FAILED);
            mediaRecord.setAsrError(exception.getMessage());
            mediaRecordMapper.updateById(mediaRecord);
            throw new BizException(
                    MockInterviewErrorCode.AUDIO_SUBMIT_FAILED.getCode(),
                    "语音回答提交失败: " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * 手动结束模拟面试会话。
     *
     * <p>强制将会话标记为结束，计算本轮平均分并生成总结，返回完整会话详情。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 结束后的会话详情 VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewSessionVO finishSession(Long userId, Long sessionId) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);
        finishAndScoreSession(session);
        return getSessionDetail(userId, sessionId);
    }

    /**
     * 创建会话并按种子批量插入题目。
     *
     * <p>题目按 sortNo 递增排序，初始状态均为未回答。
     *
     * @param session 会话实体
     * @param seeds   题目种子列表
     */
    private void createSessionAndQuestions(MockInterviewSession session, List<QuestionSeed> seeds) {
        if (seeds.isEmpty()) {
            throw mockInterviewException(MockInterviewErrorCode.NO_AVAILABLE_QUESTION, "没有可用的面试题，请检查岗位或简历内容");
        }
        session.setStatus(STATUS_IN_PROGRESS);
        session.setCurrentIndex(0);
        session.setTotalQuestionCount(seeds.size());
        session.setIsDeleted(NOT_DELETED);
        sessionMapper.insert(session);

        int sortNo = 1;
        for (QuestionSeed seed : seeds) {
            MockInterviewQuestion question = new MockInterviewQuestion();
            question.setSessionId(session.getId());
            question.setUserId(session.getUserId());
            question.setQuestionType(seed.type());
            question.setQuestionBankId(seed.questionBankId());
            question.setRagChunkId(seed.ragChunkId());
            question.setQuestionContent(seed.content());
            question.setStandardAnswer(seed.standardAnswer());
            question.setSortNo(sortNo++);
            question.setAnswered(0);
            question.setIsDeleted(NOT_DELETED);
            questionMapper.insert(question);
        }
    }

    /**
     * 校验题目是否可用于当前会话回答。
     *
     * <p>校验项：会话未结束、题目存在且归属正确、题目未被重复回答。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param questionId 题目 ID
     * @param session   当前会话
     * @return 校验通过的题目实体
     */
    private MockInterviewQuestion getQuestionForAnswer(Long userId, Long sessionId, Long questionId, MockInterviewSession session) {
        if (STATUS_FINISHED.equals(session.getStatus())) {
            throw mockInterviewException(MockInterviewErrorCode.QUESTION_NOT_AVAILABLE, "本轮模拟面试已经结束");
        }

        MockInterviewQuestion question = questionMapper.selectById(questionId);
        if (question == null || !sessionId.equals(question.getSessionId()) || !userId.equals(question.getUserId())) {
            throw mockInterviewException(MockInterviewErrorCode.QUESTION_NOT_AVAILABLE, "题目不存在或无权限回答");
        }
        if (question.getAnswered() != null && question.getAnswered() == 1) {
            throw mockInterviewException(MockInterviewErrorCode.QUESTION_ALREADY_ANSWERED, "该题已经回答过");
        }
        return question;
    }

    /**
     * 创建回答、评分并推进会话进度。
     *
     * <p>步骤：
     * <ol>
     *   <li>调用评分引擎（模型优先，规则兜底）生成评价结果。</li>
     *   <li>保存回答记录，按需沉淀错题本。</li>
     *   <li>标记题目已答，推进会话索引；最后一题自动触发会话结束。</li>
     * </ol>
     *
     * @param session       当前会话
     * @param question      当前题目
     * @param answerContent 用户回答内容（文本）
     * @return 保存后的回答实体
     */
    private MockInterviewAnswer createAnswerAndAdvance(MockInterviewSession session, MockInterviewQuestion question, String answerContent) {
        AnswerEvaluation evaluation = evaluateAnswer(question, answerContent);

        MockInterviewAnswer answer = new MockInterviewAnswer();
        answer.setSessionId(session.getId());
        answer.setQuestionId(question.getId());
        answer.setUserId(session.getUserId());
        answer.setAnswerContent(answerContent);
        answer.setScore(evaluation.score());
        answer.setLevel(evaluation.level());
        answer.setStrengths(String.join("\n", evaluation.strengths()));
        answer.setProblems(String.join("\n", evaluation.problems()));
        answer.setSuggestions(String.join("\n", evaluation.suggestions()));
        answer.setCorrectFlag(evaluation.correct() ? 1 : 0);
        answer.setSimilarityScore(evaluation.similarityScore());
        answer.setMatchedPoints(String.join("\n", evaluation.matchedPoints()));
        answer.setMissingPoints(String.join("\n", evaluation.missingPoints()));
        answer.setKnowledgePoints(String.join("\n", evaluation.knowledgePoints()));
        answer.setReviewConclusion(evaluation.reviewConclusion());
        answer.setWrongBookFlag(shouldSaveWrongQuestion(evaluation) ? 1 : 0);
        answer.setIsDeleted(NOT_DELETED);
        answerMapper.insert(answer);

        saveWrongQuestionIfNeeded(session, question, answer, evaluation);

        // 1. 标记题目已答，确保同一道题不会重复评分。
        question.setAnswered(1);
        questionMapper.updateById(question);

        // 2. 推进会话进度；最后一题自动结束并计算平均分。
        session.setCurrentIndex((session.getCurrentIndex() == null ? 0 : session.getCurrentIndex()) + 1);
        if (session.getCurrentIndex() >= session.getTotalQuestionCount()) {
            finishAndScoreSession(session);
        } else {
            sessionMapper.updateById(session);
        }
        return answer;
    }

    /**
     * 判断当前回答是否需要进入错题本。
     *
     * <p>规则：明确判错、得分低于 70、或存在缺失要点，任一条件满足即入库。
     *
     * @param evaluation 评分结果
     * @return true 表示需要收录错题
     */
    private boolean shouldSaveWrongQuestion(AnswerEvaluation evaluation) {
        /*
         * 1. 明确判错的题进入错题本。
         * 2. 低于 70 分的题即使模型没有判错，也说明表达或要点覆盖不足，需要后续复练。
         * 3. 有缺失要点时进入错题本，便于补题计划围绕缺失点召回知识。
         */
        return !evaluation.correct()
                || evaluation.score().compareTo(BigDecimal.valueOf(70)) < 0
                || !evaluation.missingPoints().isEmpty();
    }

    /**
     * 按评分结果将题目沉淀到错题本。
     *
     * <p>同一道题重复答错时覆盖最新表现并累加错误次数，用于后续复练优先级排序。
     *
     * @param session    当前会话
     * @param question   当前题目
     * @param answer     用户回答
     * @param evaluation 评分结果
     */
    private void saveWrongQuestionIfNeeded(
            MockInterviewSession session,
            MockInterviewQuestion question,
            MockInterviewAnswer answer,
            AnswerEvaluation evaluation
    ) {
        if (!shouldSaveWrongQuestion(evaluation)) {
            return;
        }

        MockInterviewWrongQuestion wrongQuestion = wrongQuestionMapper.selectOne(
                new LambdaQueryWrapper<MockInterviewWrongQuestion>()
                        .eq(MockInterviewWrongQuestion::getUserId, session.getUserId())
                        .eq(MockInterviewWrongQuestion::getQuestionId, question.getId())
                        .eq(MockInterviewWrongQuestion::getIsDeleted, NOT_DELETED)
                        .last("limit 1")
        );

        boolean isNew = wrongQuestion == null;
        if (isNew) {
            wrongQuestion = new MockInterviewWrongQuestion();
            wrongQuestion.setUserId(session.getUserId());
            wrongQuestion.setQuestionId(question.getId());
            wrongQuestion.setWrongCount(0);
            wrongQuestion.setIsDeleted(NOT_DELETED);
        }

        // 每次答错都覆盖最新表现，同时累加错误次数，后续可以按 wrongCount 做复练优先级。
        wrongQuestion.setSessionId(session.getId());
        wrongQuestion.setAnswerId(answer.getId());
        wrongQuestion.setJobId(session.getJobId());
        wrongQuestion.setResumeId(session.getResumeId());
        wrongQuestion.setQuestionType(question.getQuestionType());
        wrongQuestion.setQuestionContent(question.getQuestionContent());
        wrongQuestion.setStandardAnswer(question.getStandardAnswer());
        wrongQuestion.setLastAnswerContent(answer.getAnswerContent());
        wrongQuestion.setLastScore(answer.getScore());
        wrongQuestion.setSimilarityScore(answer.getSimilarityScore());
        wrongQuestion.setKnowledgePoints(answer.getKnowledgePoints());
        wrongQuestion.setMissingPoints(answer.getMissingPoints());
        wrongQuestion.setSuggestions(answer.getSuggestions());
        wrongQuestion.setWrongReason(buildWrongReason(evaluation));
        wrongQuestion.setWrongCount((wrongQuestion.getWrongCount() == null ? 0 : wrongQuestion.getWrongCount()) + 1);
        wrongQuestion.setMasteryStatus("UNMASTERED");

        if (isNew) {
            wrongQuestionMapper.insert(wrongQuestion);
        } else {
            wrongQuestionMapper.updateById(wrongQuestion);
        }
    }

    private String buildWrongReason(AnswerEvaluation evaluation) {
        if (!evaluation.correct()) {
            return "回答未覆盖标准答案核心要点";
        }
        if (evaluation.score().compareTo(BigDecimal.valueOf(70)) < 0) {
            return "单题得分低于 70 分";
        }
        return "存在标准答案缺失要点";
    }

    private List<QuestionSeed> buildQuestionSeeds(InterviewPrepareVO prepare, int questionCount) {
        List<QuestionSeed> seeds = new ArrayList<>();
        addSeeds(seeds, TYPE_TECHNICAL, prepare.getTechnicalQuestions());
        addSeeds(seeds, TYPE_PROJECT, prepare.getProjectQuestions());
        addSeeds(seeds, TYPE_HR, prepare.getHrQuestions());
        return limitDistinctSeeds(seeds, questionCount);
    }

    private List<QuestionSeed> buildQuestionSeedsFromBank(List<InterviewQuestionBank> questions) {
        if (questions == null || questions.isEmpty()) {
            return new ArrayList<>();
        }
        return questions.stream()
                .map(this::toQuestionSeed)
                .toList();
    }

    private QuestionSeed toQuestionSeed(InterviewQuestionBank question) {
        return new QuestionSeed(
                defaultIfBlank(question.getQuestionType(), TYPE_TECHNICAL),
                question.getQuestionTitle(),
                question.getId(),
                question.getStandardAnswer(),
                question.getRagChunkId()
        );
    }

    private List<QuestionSeed> buildAiQuestionSeeds(JobPosition job, JobResume resume, int questionCount) {
        List<QuestionSeed> seeds = new ArrayList<>();
        for (String skill : splitKeywords(job.getSkillKeywords())) {
            seeds.add(ruleSeed(TYPE_TECHNICAL, "请结合你的项目经历，说明你如何使用 " + skill + " 解决实际问题？"));
        }

        String jobText = safe(job.getJobDescription()) + "\n" + safe(job.getJobRequirement()) + "\n" + safe(job.getSkillKeywords());
        if (containsAny(jobText, List.of("Java", "Spring", "后端", "接口"))) {
            seeds.add(ruleSeed(TYPE_TECHNICAL, "请介绍一个你负责过的后端接口，从设计、实现、性能和异常处理几个方面展开。"));
        }
        if (containsAny(jobText, List.of("MySQL", "SQL", "数据库"))) {
            seeds.add(ruleSeed(TYPE_TECHNICAL, "如果线上 SQL 变慢，你会如何定位并优化？"));
        }
        if (containsAny(jobText, List.of("Redis", "缓存"))) {
            seeds.add(ruleSeed(TYPE_TECHNICAL, "请说明你在项目里如何使用缓存，以及如何处理缓存穿透、击穿或一致性问题。"));
        }

        if (StringUtils.hasText(resume.getRawText())) {
            seeds.add(ruleSeed(TYPE_PROJECT, "请选择简历里最能代表你能力的一个项目，说明背景、职责、难点和结果。"));
            seeds.add(ruleSeed(TYPE_PROJECT, "请讲一个你在项目中排查问题或优化性能的经历，重点说明过程和结果。"));
        }

        seeds.add(ruleSeed(TYPE_HR, "请做一个 1 分钟左右的自我介绍，并突出你和该岗位匹配的经历。"));
        seeds.add(ruleSeed(TYPE_HR, "你为什么想面试这个岗位？你认为自己最匹配的优势是什么？"));
        seeds.add(ruleSeed(TYPE_HR, "如果入职后发现业务复杂度高于预期，你会如何适应？"));

        return limitDistinctSeeds(seeds, questionCount);
    }

    private void addSeeds(List<QuestionSeed> seeds, String type, List<String> contents) {
        if (contents == null) {
            return;
        }
        for (String content : contents) {
            seeds.add(ruleSeed(type, content));
        }
    }

    private QuestionSeed ruleSeed(String type, String content) {
        return new QuestionSeed(type, content, null, null, null);
    }

    private List<QuestionSeed> limitDistinctSeeds(List<QuestionSeed> seeds, int questionCount) {
        Set<String> seen = new LinkedHashSet<>();
        List<QuestionSeed> result = new ArrayList<>();
        for (QuestionSeed seed : seeds) {
            if (StringUtils.hasText(seed.content()) && seen.add(seed.content().trim())) {
                result.add(seed);
            }
            if (result.size() >= questionCount) {
                break;
            }
        }
        return result;
    }

    /**
     * 对用户回答进行评分。
     *
     * <p>优先使用 LLM 语义评分（需题库题有标准答案）；模型失败或无标准答案时降级为规则评分，
     * 从回答长度、结构化表达、量化描述、技术关键词等维度打分。
     *
     * @param question      当前题目
     * @param answerContent 用户回答内容
     * @return 评分结果
     */
    private AnswerEvaluation evaluateAnswer(MockInterviewQuestion question, String answerContent) {
        if (StringUtils.hasText(question.getStandardAnswer())) {
            AnswerEvaluation llmEvaluation = evaluateAnswerWithStandardAnswer(question, answerContent);
            if (llmEvaluation != null) {
                return llmEvaluation;
            }
        }

        String answer = answerContent == null ? "" : answerContent.trim();
        double score = 40;
        List<String> strengths = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (answer.length() >= 120) {
            score += 20;
            strengths.add("回答内容较完整，具备一定展开。");
        } else if (answer.length() >= 60) {
            score += 10;
            strengths.add("回答具备基本内容，但展开还不够充分。");
        } else {
            problems.add("回答偏短，信息量不足。");
            suggestions.add("建议至少按照“背景、做法、结果”展开回答。");
        }

        if (containsAny(answer, List.of("首先", "其次", "最后", "第一", "第二", "背景", "方案", "结果"))) {
            score += 15;
            strengths.add("回答有一定结构，便于面试官理解。");
        } else {
            problems.add("回答结构不够清晰。");
            suggestions.add("建议使用“背景-方案-职责-结果”或“问题-分析-解决-效果”的结构。");
        }

        if (containsAny(answer, List.of("%", "提升", "降低", "优化", "QPS", "响应时间", "并发", "ms", "数据量"))) {
            score += 15;
            strengths.add("回答中包含结果或量化描述，说服力较强。");
        } else {
            suggestions.add("可以补充量化结果，例如性能提升比例、响应时间、数据量或业务效果。");
        }

        if (containsAny(answer, List.of("Java", "Spring", "MySQL", "Redis", "接口", "缓存", "事务", "索引", "消息队列"))) {
            score += 10;
            strengths.add("回答中体现了一定技术关键词。");
        } else if (TYPE_TECHNICAL.equals(question.getQuestionType())) {
            problems.add("技术题回答中缺少明确技术关键词。");
            suggestions.add("回答技术题时建议说出使用的框架、组件、原理或排查方法。");
        }

        BigDecimal finalScore = BigDecimal.valueOf(Math.min(score, 100)).setScale(2, RoundingMode.HALF_UP);
        String level = resolveLevel(finalScore);

        if (strengths.isEmpty()) {
            strengths.add("回答已经覆盖了部分问题方向。");
        }
        if (problems.isEmpty()) {
            problems.add("暂无明显问题，可以继续提升表达细节。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("继续保持结构化表达，并结合项目细节进行说明。");
        }
        boolean correct = finalScore.compareTo(BigDecimal.valueOf(70)) >= 0;
        List<String> matchedPoints = strengths.stream().limit(4).toList();
        List<String> missingPoints = problems.stream().limit(4).toList();
        List<String> knowledgePoints = inferKnowledgePoints(question, answer);
        String reviewConclusion = correct
                ? "回答基本覆盖题目方向，但仍可补充关键细节。"
                : "回答未充分覆盖题目关键点，需要围绕缺失要点复练。";
        return new AnswerEvaluation(
                finalScore,
                level,
                strengths,
                problems,
                suggestions,
                correct,
                finalScore,
                matchedPoints,
                missingPoints,
                knowledgePoints,
                reviewConclusion
        );
    }

    /**
     * 调用 LLM 对用户回答与标准答案进行语义匹配评分。
     *
     * <p>通过统一模型网关调用评分场景 Prompt；若模型配置缺失、超时或 JSON 解析失败，返回 {@code null}，
     * 由上层自动降级到规则评分。
     *
     * @param question      当前题目
     * @param answerContent 用户回答内容
     * @return 模型评分结果，失败时返回 {@code null}
     */
    private AnswerEvaluation evaluateAnswerWithStandardAnswer(MockInterviewQuestion question, String answerContent) {
        try {
            /*
             * 1. 题库题有标准答案时，优先让模型做语义匹配，而不是只靠关键词长度规则。
             * 2. 调用统一模型网关，模型、Prompt、重试、熔断、调用日志都继续由后台配置管理。
             * 3. 如果模型配置缺失、超时或 JSON 解析失败，返回 null，让上层自动走规则兜底评分。
             */
            String userMessage = buildAnswerEvaluatePrompt(question, answerContent);
            String response = aiModelGatewayService.chat(
                    AI_SCENE_MOCK_INTERVIEW_ANSWER_EVALUATE,
                    buildAnswerEvaluateVariables(question, answerContent, userMessage),
                    userMessage,
                    question.getUserId(),
                    buildAnswerEvaluateTraceId(question)
            );
            AnswerEvaluateModelResult modelResult = objectMapper.readValue(extractJson(response), AnswerEvaluateModelResult.class);
            return normalizeModelEvaluation(modelResult);
        } catch (Exception exception) {
            return null;
        }
    }

    private Map<String, Object> buildAnswerEvaluateVariables(
            MockInterviewQuestion question,
            String answerContent,
            String fullPrompt
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("question", question.getQuestionContent());
        variables.put("question_content", question.getQuestionContent());
        variables.put("questionType", question.getQuestionType());
        variables.put("question_type", question.getQuestionType());
        variables.put("standardAnswer", question.getStandardAnswer());
        variables.put("standard_answer", question.getStandardAnswer());
        variables.put("userAnswer", answerContent);
        variables.put("user_answer", answerContent);
        variables.put("fullPrompt", fullPrompt);
        variables.put("full_prompt", fullPrompt);
        variables.put("jsonFormat", "只输出 JSON 对象，不要 Markdown，不要解释文本。");
        variables.put("json_format", variables.get("jsonFormat"));
        return variables;
    }

    private String buildAnswerEvaluatePrompt(MockInterviewQuestion question, String answerContent) {
        return """
                请你作为面试评分官，对比用户回答和标准答案，判断语义是否相近，并输出 JSON。
                
                评分规则:
                1. 只要用户回答覆盖标准答案的核心含义，即使表述不同，也算正确或部分正确。
                2. 不要求逐字一致，重点看关键知识点、因果关系、解决步骤是否覆盖。
                3. score 范围 0-100，isCorrect 表示是否基本正确。
                4. matchedPoints、missingPoints、suggestions 每个数组最多 4 条，每条不超过 40 字。
                5. level 只能是 优秀、良好、一般、待提升。
                6. 只输出 JSON，不要 Markdown。
                
                JSON 字段:
                {
                  "score": 85,
                  "isCorrect": true,
                  "level": "良好",
                  "matchedPoints": ["覆盖了核心概念"],
                  "missingPoints": ["缺少具体例子"],
                  "suggestions": ["补充项目中的实际使用场景"]
                }
                
                题目:
                %s
                
                标准答案:
                %s
                
                用户回答:
                %s
                """.formatted(
                safe(question.getQuestionContent()),
                safe(question.getStandardAnswer()),
                safe(answerContent)
        );
    }

    private AnswerEvaluation normalizeModelEvaluation(AnswerEvaluateModelResult result) {
        if (result == null || result.score() == null) {
            return null;
        }

        BigDecimal score = BigDecimal.valueOf(Math.max(0, Math.min(100, result.score())))
                .setScale(2, RoundingMode.HALF_UP);
        String level = StringUtils.hasText(result.level()) ? result.level().trim() : resolveLevel(score);

        List<String> strengths = limitTextList(result.matchedPoints(), "回答覆盖了部分标准答案要点。");
        List<String> problems = limitTextList(result.missingPoints(), "仍有部分关键点没有展开。");
        List<String> suggestions = limitTextList(result.suggestions(), "建议围绕标准答案补充关键知识点和项目例子。");
        List<String> knowledgePoints = limitTextList(result.knowledgePoints(), "题目相关基础知识");
        boolean correct = result.isCorrect() != null ? result.isCorrect() : score.compareTo(BigDecimal.valueOf(70)) >= 0;
        BigDecimal similarityScore = result.similarityScore() == null
                ? score
                : BigDecimal.valueOf(Math.max(0, Math.min(100, result.similarityScore()))).setScale(2, RoundingMode.HALF_UP);
        String reviewConclusion = StringUtils.hasText(result.reviewConclusion())
                ? result.reviewConclusion().trim()
                : buildModelReviewConclusion(correct, score, problems);
        return new AnswerEvaluation(
                score,
                level,
                strengths,
                problems,
                suggestions,
                correct,
                similarityScore,
                strengths,
                problems,
                knowledgePoints,
                reviewConclusion
        );
    }

    private List<String> limitTextList(List<String> values, String defaultValue) {
        if (values == null || values.isEmpty()) {
            return List.of(defaultValue);
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(4)
                .toList();
    }

    private List<String> inferKnowledgePoints(MockInterviewQuestion question, String answer) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        String text = safe(question.getQuestionContent()) + " " + safe(question.getStandardAnswer()) + " " + safe(answer);

        // 第一版用轻量规则兜底，避免模型不可用时单题复盘缺少知识点。
        if (containsAny(text, List.of("MySQL", "SQL", "索引", "数据库"))) {
            points.add("MySQL / SQL");
        }
        if (containsAny(text, List.of("Redis", "缓存"))) {
            points.add("Redis / 缓存");
        }
        if (containsAny(text, List.of("Spring", "SpringBoot", "接口"))) {
            points.add("Spring / 后端接口");
        }
        if (containsAny(text, List.of("项目", "职责", "结果", "背景"))) {
            points.add("项目表达");
        }
        if (points.isEmpty()) {
            points.add(defaultIfBlank(question.getQuestionType(), "面试表达"));
        }
        return points.stream().limit(4).toList();
    }

    private String buildModelReviewConclusion(boolean correct, BigDecimal score, List<String> missingPoints) {
        if (correct && score.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return "回答与标准答案相近，关键要点覆盖较完整。";
        }
        if (correct) {
            return "回答基本正确，但仍有部分要点可以展开。";
        }
        String missingSummary = missingPoints == null || missingPoints.isEmpty() ? "核心要点" : String.join("、", missingPoints);
        return "回答与标准答案差距较大，需要补充：" + truncate(missingSummary, 80);
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw new IllegalArgumentException("模型未返回内容");
        }

        String cleaned = response.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型未返回合法 JSON: " + cleaned);
        }
        return cleaned.substring(start, end + 1);
    }

    private String buildAnswerEvaluateTraceId(MockInterviewQuestion question) {
        return "mock_answer_eval_"
                + question.getUserId()
                + "_"
                + question.getSessionId()
                + "_"
                + question.getId()
                + "_"
                + UUID.randomUUID();
    }

    /**
     * 结束会话并计算本轮平均分与总结。
     *
     * @param session 当前会话
     */
    private void finishAndScoreSession(MockInterviewSession session) {
        List<MockInterviewAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<MockInterviewAnswer>()
                .eq(MockInterviewAnswer::getSessionId, session.getId())
                .eq(MockInterviewAnswer::getUserId, session.getUserId())
                .eq(MockInterviewAnswer::getIsDeleted, NOT_DELETED));

        BigDecimal totalScore = BigDecimal.ZERO;
        if (!answers.isEmpty()) {
            BigDecimal sum = answers.stream().map(MockInterviewAnswer::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalScore = sum.divide(BigDecimal.valueOf(answers.size()), 2, RoundingMode.HALF_UP);
        }

        session.setStatus(STATUS_FINISHED);
        session.setTotalScore(totalScore);
        session.setSummary(buildSessionSummary(totalScore));
        sessionMapper.updateById(session);
    }

    private String buildSessionSummary(BigDecimal totalScore) {
        double value = totalScore.doubleValue();
        if (value >= 85) {
            return "本轮模拟面试表现较好，回答较完整，建议继续加强项目细节和量化表达。";
        }
        if (value >= 70) {
            return "本轮模拟面试表现中等偏上，具备基本表达能力，但部分回答还可以更结构化。";
        }
        if (value >= 60) {
            return "本轮模拟面试表现一般，建议重点练习项目介绍、技术原理和回答结构。";
        }
        return "本轮模拟面试表现偏弱，建议先整理简历项目话术，再进行下一轮模拟。";
    }

    /**
     * 获取并校验用户会话归属与有效性。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 校验通过的会话实体
     */
    private MockInterviewSession getUserSessionRequired(Long userId, Long sessionId) {
        MockInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId()) || (session.getIsDeleted() != null && session.getIsDeleted() == 1)) {
            throw mockInterviewException(MockInterviewErrorCode.SESSION_NOT_FOUND, "模拟面试会话不存在或无权限访问");
        }
        return session;
    }

    /**
     * 创建 AI 面试标准业务异常。
     *
     * 步骤:
     * 1. 使用枚举里的稳定 errorCode，前端可据此展示具体失败步骤。
     * 2. message 仍然保留用户可读提示，兼容旧的通用错误展示。
     * 3. 所有 AI 面试链路错误统一从这里创建，避免硬编码字符串分散。
     */
    private BizException mockInterviewException(MockInterviewErrorCode errorCode, String message) {
        return new BizException(errorCode.getCode(), defaultIfBlank(message, errorCode.getDefaultMessage()));
    }

    private int normalizeQuestionCount(Integer questionCount) {
        int value = questionCount == null ? 6 : questionCount;
        return Math.max(3, Math.min(value, 12));
    }

    private List<String> splitKeywords(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split("[,，、\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean containsAny(String text, List<String> keywords) {
        String lowerText = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String resolveLevel(BigDecimal score) {
        double value = score.doubleValue();
        if (value >= 85) {
            return "优秀";
        }
        if (value >= 70) {
            return "良好";
        }
        if (value >= 60) {
            return "一般";
        }
        return "待提升";
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record QuestionSeed(
            String type,
            String content,
            Long questionBankId,
            String standardAnswer,
            Long ragChunkId
    ) {
    }

    private record AnswerEvaluateModelResult(
            Double score,
            Boolean isCorrect,
            String level,
            Double similarityScore,
            List<String> matchedPoints,
            List<String> missingPoints,
            List<String> knowledgePoints,
            String reviewConclusion,
            List<String> suggestions
    ) {
    }

    private record AnswerEvaluation(
            BigDecimal score,
            String level,
            List<String> strengths,
            List<String> problems,
            List<String> suggestions,
            boolean correct,
            BigDecimal similarityScore,
            List<String> matchedPoints,
            List<String> missingPoints,
            List<String> knowledgePoints,
            String reviewConclusion
    ) {
    }
}
