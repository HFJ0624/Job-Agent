package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.MockInterviewAnswerMapper;
import com.job.bootstrap.mapper.MockInterviewMediaRecordMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.service.FileStorageService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.bootstrap.service.InterviewQuestionSelectorService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.MockInterviewService;
import com.job.bootstrap.service.SpeechRecognitionService;
import com.job.common.dto.interview.AiInterviewStartDTO;
import com.job.common.dto.interview.MockInterviewAnswerDTO;
import com.job.common.dto.interview.MockInterviewStartDTO;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.interview.InterviewQuestionBank;
import com.job.common.entity.interview.MockInterviewAnswer;
import com.job.common.entity.interview.MockInterviewMediaRecord;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.interview.MockInterviewSession;
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
 * 功能: 模拟面试服务实现。
 *
 * 说明:
 * 1. 保留原来的求职记录模拟面试能力，兼容已有页面。
 * 2. 新增按“简历 + 岗位”直接启动 AI 语音面试，适合用户主动练习。
 * 3. 音频答题先保存原始文件和 ASR 结果，再复用文本答题评分链路，保证数据可追溯。
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
    private final JobApplicationRecordMapper applicationMapper;
    private final InterviewPrepareService interviewPrepareService;
    private final InterviewQuestionSelectorService interviewQuestionSelectorService;
    private final JobResumeService jobResumeService;
    private final JobPositionService jobPositionService;
    private final FileStorageService fileStorageService;
    private final SpeechRecognitionService speechRecognitionService;
    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewSessionVO startSession(Long userId, MockInterviewStartDTO dto) {
        JobApplicationRecord application = applicationMapper.selectById(dto.getApplicationId());
        if (application == null || !userId.equals(application.getUserId())) {
            throw new BizException("求职记录不存在或无权限访问");
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
            throw new BizException("岗位未发布，不能用于 AI 面试");
        }

        // 3. 根据岗位 JD、技能关键词和简历文本生成第一版问题，不依赖用户先创建求职记录。
        int questionCount = normalizeQuestionCount(dto.getQuestionCount());
        List<QuestionSeed> seeds = buildQuestionSeedsFromBank(
                interviewQuestionSelectorService.selectQuestions(
                        userId,
                        job,
                        resume,
                        questionCount,
                        dto.getExcludeRecentHours()
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewAnswerVO submitAnswer(Long userId, Long sessionId, MockInterviewAnswerDTO dto) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);
        MockInterviewQuestion question = getQuestionForAnswer(userId, sessionId, dto.getQuestionId(), session);
        MockInterviewAnswer answer = createAnswerAndAdvance(session, question, dto.getAnswerContent());
        return MockInterviewAnswerVO.from(answer);
    }

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
                throw new BizException("语音识别失败: " + asrResult.errorMessage());
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
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "语音回答提交失败: " + exception.getMessage(), exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewSessionVO finishSession(Long userId, Long sessionId) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);
        finishAndScoreSession(session);
        return getSessionDetail(userId, sessionId);
    }

    private void createSessionAndQuestions(MockInterviewSession session, List<QuestionSeed> seeds) {
        if (seeds.isEmpty()) {
            throw new BizException("没有可用的面试题，请检查岗位或简历内容");
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

    private MockInterviewQuestion getQuestionForAnswer(Long userId, Long sessionId, Long questionId, MockInterviewSession session) {
        if (STATUS_FINISHED.equals(session.getStatus())) {
            throw new BizException("本轮模拟面试已经结束");
        }

        MockInterviewQuestion question = questionMapper.selectById(questionId);
        if (question == null || !sessionId.equals(question.getSessionId()) || !userId.equals(question.getUserId())) {
            throw new BizException("题目不存在或无权限回答");
        }
        if (question.getAnswered() != null && question.getAnswered() == 1) {
            throw new BizException("该题已经回答过");
        }
        return question;
    }

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
        answer.setIsDeleted(NOT_DELETED);
        answerMapper.insert(answer);

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
        return new AnswerEvaluation(finalScore, level, strengths, problems, suggestions);
    }

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
        return new AnswerEvaluation(score, level, strengths, problems, suggestions);
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

    private MockInterviewSession getUserSessionRequired(Long userId, Long sessionId) {
        MockInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId()) || (session.getIsDeleted() != null && session.getIsDeleted() == 1)) {
            throw new BizException("模拟面试会话不存在或无权限访问");
        }
        return session;
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
            List<String> matchedPoints,
            List<String> missingPoints,
            List<String> suggestions
    ) {
    }

    private record AnswerEvaluation(
            BigDecimal score,
            String level,
            List<String> strengths,
            List<String> problems,
            List<String> suggestions
    ) {
    }
}
