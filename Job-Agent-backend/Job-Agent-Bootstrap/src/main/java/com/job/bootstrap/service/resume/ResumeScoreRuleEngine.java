package com.job.bootstrap.service.resume;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 作者:hfj
 * 功能:AI 简历评分 V2 规则引擎
 * 日期:2026/6/15
 *
 * 设计说明:
 * 1. 本类负责生成稳定、可复现的初始规则分。
 * 2. 本类不调用数据库，也不调用大模型，方便单元测试和本地兜底。
 * 3. 业务 Service 会把本类输出交给大模型二次评分，再按权重合并最终分。
 * 4. 如果大模型不可用，本类输出会作为明确标记的兜底结果返回。
 */
@Component
public class ResumeScoreRuleEngine {

    private static final String SCORE_VERSION = "V2";

    /**
     * 邮箱正则，用于判断基础联系方式是否完整。
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * 手机号正则，只做简历质量评分，不做真实号码归属校验。
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /**
     * 量化成果正则，用来识别“提升 30% / 300ms / 10 万用户 / QPS 500”等表达。
     */
    private static final Pattern QUANTIFIED_PATTERN = Pattern.compile(
            "(?i)(\\d+(\\.\\d+)?\\s*(%|ms|s|秒|分钟|QPS|TPS|万|k|K|人|次|条|个|元|年))|((提升|降低|减少|优化|增长|提高|缩短|节省)\\s*\\d+)"
    );

    private static final List<String> CITY_KEYWORDS = List.of(
            "北京", "上海", "广州", "深圳", "杭州", "南京", "苏州", "成都", "武汉", "西安", "长沙", "郑州",
            "现居", "所在地", "城市", "求职地点", "期望城市", "Location"
    );

    private static final List<String> CAREER_DIRECTION_KEYWORDS = List.of(
            "求职意向", "目标岗位", "应聘岗位", "期望岗位", "职业目标", "个人简介",
            "Java 后端", "Java开发", "后端开发", "Backend", "AI Agent", "Agent 开发", "RAG", "全栈开发",
            "前端开发", "数据分析", "算法", "测试开发"
    );

    private static final List<String> MIXED_DIRECTION_KEYWORDS = List.of(
            "后端", "前端", "数据分析", "产品经理", "运营", "测试", "算法", "设计"
    );

    private static final List<String> EDUCATION_KEYWORDS = List.of(
            "大学", "学院", "本科", "硕士", "研究生", "专科", "博士", "专业", "学士", "学历", "Education"
    );

    private static final List<String> COURSE_KEYWORDS = List.of(
            "主修课程", "相关课程", "核心课程", "课程", "数据结构", "计算机网络", "操作系统", "数据库", "软件工程"
    );

    private static final List<String> HONOR_KEYWORDS = List.of(
            "GPA", "绩点", "奖学金", "荣誉", "竞赛", "获奖", "证书", "CET", "英语六级", "英语四级"
    );

    private static final List<String> SKILL_CATEGORY_KEYWORDS = List.of(
            "Languages", "Backend", "Database", "DevOps", "Frontend", "AI", "技能", "技术栈",
            "后端", "数据库", "缓存", "消息队列", "工程化", "前端", "人工智能"
    );

    private static final List<String> CORE_LANGUAGE_KEYWORDS = List.of(
            "Java", "Python", "Go", "C++", "JavaScript", "TypeScript", "SQL"
    );

    private static final List<String> FRAMEWORK_KEYWORDS = List.of(
            "Spring Boot", "Spring Cloud", "Spring", "MyBatis", "MyBatis-Plus", "Vue", "React",
            "LangChain4j", "LangChain", "OpenAI", "FastAPI"
    );

    private static final List<String> DATABASE_MIDDLEWARE_KEYWORDS = List.of(
            "MySQL", "PostgreSQL", "pgvector", "Redis", "MongoDB", "Elasticsearch", "RabbitMQ", "Kafka", "RocketMQ"
    );

    private static final List<String> ENGINEERING_KEYWORDS = List.of(
            "Git", "Maven", "Docker", "Kubernetes", "Linux", "Nginx", "CI/CD", "Jenkins", "部署", "监控", "日志"
    );

    private static final List<String> AI_AGENT_KEYWORDS = List.of(
            "AI Agent", "Agent", "RAG", "Embedding", "向量库", "pgvector", "MCP", "Tool Calling", "LangChain4j",
            "Prompt", "大模型", "LLM", "OpenAI", "豆包", "火山引擎"
    );

    private static final List<String> PROJECT_KEYWORDS = List.of(
            "项目", "Project", "系统", "平台", "模块", "业务", "技术栈", "核心功能", "项目背景"
    );

    private static final List<String> RESPONSIBILITY_KEYWORDS = List.of(
            "负责", "独立", "参与", "主导", "设计", "实现", "开发", "封装", "重构", "排查", "优化", "落地"
    );

    private static final List<String> DEPTH_KEYWORDS = List.of(
            "架构", "权限", "缓存", "消息队列", "分布式", "高并发", "事务", "索引", "限流", "幂等", "异步",
            "RAG", "向量检索", "Tool", "Trace", "调用链路", "多轮对话", "上下文"
    );

    private static final List<String> DIFFICULTY_KEYWORDS = List.of(
            "难点", "问题", "挑战", "解决方案", "方案", "瓶颈", "优化", "排查", "定位", "改造"
    );

    private static final List<String> WORK_KEYWORDS = List.of(
            "实习", "工作经历", "任职", "公司", "岗位", "职责", "Intern", "Experience"
    );

    private static final List<String> BUSINESS_CONTRIBUTION_KEYWORDS = List.of(
            "上线", "交付", "维护", "业务", "用户", "订单", "转化", "效率", "成本", "收益", "稳定性"
    );

    private static final List<String> PROFESSIONAL_VERBS = List.of(
            "负责", "实现", "设计", "优化", "重构", "封装", "接入", "构建", "排查", "落地", "提升", "降低"
    );

    /**
     * 基于简历原文和可选求职方向计算 V2 评分。
     *
     * @param resumeText 简历解析后的纯文本
     * @param targetPosition 用户填写的求职方向，可为空；它只用于辅助判断方向一致性，不把 JD 当成评分标准
     * @return 稳定的规则评分结果
     */
    public RuleScoreResult calculate(String resumeText, String targetPosition) {
        String text = normalizeText(resumeText);
        String target = normalizeText(targetPosition);

        List<ScoreDimension> dimensions = List.of(
                scoreBasicInfo(text),
                scoreCareerGoal(text, target),
                scoreEducation(text),
                scoreSkills(text, target),
                scoreProjectExperience(text, target),
                scoreWorkExperience(text, target),
                scoreQuantifiedImpact(text),
                scoreFormat(text)
        );

        ScoreBreakdown breakdown = buildBreakdown(dimensions);
        int total = dimensions.stream()
                .mapToInt(ScoreDimension::getScore)
                .sum();

        RuleScoreResult result = new RuleScoreResult();
        result.setScoreVersion(SCORE_VERSION);
        result.setScoringMode("RULE_SCORE_INITIAL");
        result.setOverallScore(total);
        result.setLevel(resolveLevel(total));
        result.setScoreBreakdown(breakdown);
        result.setDimensions(dimensions);
        result.setStrengths(buildStrengths(dimensions, target));
        result.setWeaknesses(buildWeaknesses(dimensions));
        result.setRiskPoints(buildRiskPoints(dimensions));
        result.setImprovementSuggestions(buildImprovementSuggestions(dimensions));
        result.setSummary(buildSummary(total, target));
        result.setLlmStatus("SKIPPED");
        return result;
    }

    /**
     * 基础信息完整性，满分 10。
     */
    private ScoreDimension scoreBasicInfo(String text) {
        int score = 0;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (hasLikelyName(text)) {
            score += 2;
        } else {
            issues.add("姓名或候选人标识不够清楚。");
            suggestions.add("在简历顶部保留真实姓名，并让姓名成为第一屏最容易看到的信息。");
        }

        boolean hasEmail = EMAIL_PATTERN.matcher(text).find();
        boolean hasPhone = PHONE_PATTERN.matcher(text).find();
        if (hasEmail && hasPhone) {
            score += 2;
        } else if (hasEmail || hasPhone) {
            score += 1;
            issues.add("联系方式不完整，邮箱和手机号至少缺少一项。");
            suggestions.add("补全邮箱和手机号，方便 HR 或招聘系统直接联系。");
        } else {
            issues.add("简历中未找到邮箱或手机号。");
            suggestions.add("在基础信息区补充手机号和专业邮箱。");
        }

        if (containsAny(text, CITY_KEYWORDS)) {
            score += 1;
        } else {
            issues.add("所在城市或期望城市不明确。");
            suggestions.add("补充现居城市或期望城市，例如“上海 / 可实习 / 可到岗时间”。");
        }

        if (containsAny(text, List.of("GitHub", "Gitee", "github.com", "gitee.com", "开源"))) {
            score += 2;
        } else {
            issues.add("简历中未找到 GitHub、Gitee 或开源作品链接。");
            suggestions.add("如果有项目仓库，建议放在基础信息区或项目标题旁。");
        }

        if (containsAny(text, List.of("LinkedIn", "领英", "个人网站", "博客", "Blog", "CSDN", "掘金", "作品集", "http"))) {
            score += 1;
        } else {
            suggestions.add("可以补充个人博客、技术文章或作品集链接，增强可信度。");
        }

        if (!hasObviousTextNoise(text)) {
            score += 2;
        } else {
            issues.add("文本中疑似存在解析噪声、乱码或图片占位符。");
            suggestions.add("优先上传可复制文字的 PDF / DOCX，并减少把核心内容放进图片。");
        }

        return dimension(
                "基础信息完整性",
                score,
                10,
                score >= 8 ? "基础联系方式和个人入口较完整。" : "基础信息仍有缺口，会影响 HR 联系和项目可信度。",
                issues,
                suggestions
        );
    }

    /**
     * 求职目标清晰度，满分 10。
     */
    private ScoreDimension scoreCareerGoal(String text, String targetPosition) {
        int score = 0;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (containsAny(text, CAREER_DIRECTION_KEYWORDS)) {
            score += 4;
        } else {
            issues.add("简历中没有清晰出现求职意向或目标方向。");
            suggestions.add("在简历顶部补充“求职意向”，例如“Java 后端开发 / AI Agent 应用开发”。");
        }

        if (isDirectionConsistent(text, targetPosition)) {
            score += 3;
        } else {
            issues.add("技能、项目和目标方向之间的关联不够明显。");
            suggestions.add("把最匹配目标方向的技能和项目放在前面，减少无关方向的堆砌。");
        }

        if (containsAny(text, List.of("个人简介", "自我评价", "Profile", "Summary", "优势", "亮点"))) {
            score += 2;
        } else {
            suggestions.add("增加 2 到 3 行个人简介，概括技术方向、核心项目和求职目标。");
        }

        int mixedCount = countKeywordHits(text, MIXED_DIRECTION_KEYWORDS);
        if (mixedCount <= 3) {
            score += 1;
        } else {
            issues.add("简历中同时出现较多不同方向，可能让招聘方看不清主线。");
            suggestions.add("针对目标岗位保留主线技术，弱化产品、运营、测试等非目标方向内容。");
        }

        return dimension(
                "求职目标清晰度",
                score,
                10,
                score >= 8 ? "简历可以较清楚地看出候选人的求职方向。" : "求职方向表达还不够聚焦。",
                issues,
                suggestions
        );
    }

    /**
     * 教育背景，满分 10。
     */
    private ScoreDimension scoreEducation(String text) {
        int score = 0;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        boolean hasEducation = containsAny(text, EDUCATION_KEYWORDS);
        boolean hasTime = hasYearRange(text);
        if (hasEducation && hasTime) {
            score += 3;
        } else if (hasEducation) {
            score += 2;
            issues.add("教育经历缺少清晰时间线。");
            suggestions.add("教育经历建议写成“学校 / 专业 / 学历 / 起止时间”。");
        } else {
            issues.add("简历中未找到完整教育背景。");
            suggestions.add("补充学校、专业、学历、起止时间。");
        }

        if (containsAny(text, COURSE_KEYWORDS)) {
            score += 2;
        } else {
            suggestions.add("应届或低年限候选人可以补充与岗位相关的核心课程。");
        }

        if (containsAny(text, HONOR_KEYWORDS)) {
            score += 2;
        } else {
            suggestions.add("如果有 GPA、奖学金、竞赛或证书，建议放到教育背景下。");
        }

        if (containsAny(text, List.of("毕设", "毕业设计", "课程设计", "论文", "科研", "实验室"))) {
            score += 2;
        } else {
            suggestions.add("如果没有实习经历，可以把毕设、课程项目或科研经历写得更工程化。");
        }

        if (hasEducation && text.contains("\n")) {
            score += 1;
        } else if (hasEducation) {
            suggestions.add("教育信息建议单独成段，避免和技能或项目混在一起。");
        }

        return dimension(
                "教育背景",
                score,
                10,
                score >= 8 ? "教育背景信息较完整，能支撑候选人基础背景。" : "教育背景信息可以继续补充完整。",
                issues,
                suggestions
        );
    }

    /**
     * 技能结构，满分 15。
     */
    private ScoreDimension scoreSkills(String text, String targetPosition) {
        int score = 0;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (containsAny(text, SKILL_CATEGORY_KEYWORDS)) {
            score += 3;
        } else {
            issues.add("技能栈缺少清晰分类。");
            suggestions.add("技能建议按 Languages / Backend / Database / AI Agent / DevOps 分类展示。");
        }

        if (containsAny(text, CORE_LANGUAGE_KEYWORDS)) {
            score += 3;
        } else {
            issues.add("核心编程语言不突出。");
            suggestions.add("把最核心的语言放在技能栈第一行，例如 Java、Python、TypeScript。");
        }

        if (containsAny(text, FRAMEWORK_KEYWORDS)) {
            score += 3;
        } else {
            issues.add("框架或工程框架信息不足。");
            suggestions.add("补充 Spring Boot、MyBatis、LangChain4j 等真实使用过的框架。");
        }

        if (containsAny(text, DATABASE_MIDDLEWARE_KEYWORDS)) {
            score += 2;
        } else {
            suggestions.add("后端方向建议补充数据库、缓存、消息队列等基础设施经验。");
        }

        if (containsAny(text, ENGINEERING_KEYWORDS)) {
            score += 2;
        } else {
            suggestions.add("补充 Git、Docker、Linux、部署、日志、监控等工程化能力。");
        }

        if (containsAny(text, AI_AGENT_KEYWORDS)) {
            score += 2;
        } else if (containsAny(targetPosition, List.of("AI", "Agent", "RAG", "大模型"))) {
            issues.add("目标方向包含 AI / Agent，但技能栈中缺少对应证据。");
            suggestions.add("如果要投 Agent 开发岗位，建议补充 RAG、Embedding、Tool Calling、MCP、LangChain4j 等能力。");
        }

        return dimension(
                "技能结构",
                score,
                15,
                score >= 12 ? "技能结构覆盖了较多岗位核心能力。" : "技能结构需要进一步分类和补证据。",
                issues,
                suggestions
        );
    }

    /**
     * 项目经历质量，满分 25。
     */
    private ScoreDimension scoreProjectExperience(String text, String targetPosition) {
        int score = 0;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (containsAny(text, PROJECT_KEYWORDS)) {
            score += 3;
        } else {
            issues.add("简历中未找到明确项目经历。");
            suggestions.add("至少补充 1 到 2 个核心项目，并写清项目背景、技术栈、职责和成果。");
        }

        if (containsAny(text, FRAMEWORK_KEYWORDS) || containsAny(text, DATABASE_MIDDLEWARE_KEYWORDS)) {
            score += 3;
        } else {
            issues.add("项目中技术栈不够明确。");
            suggestions.add("每个项目建议单独写“技术栈”，不要只写业务功能。");
        }

        if (containsAny(text, RESPONSIBILITY_KEYWORDS)) {
            score += 5;
        } else {
            issues.add("项目中个人职责不清晰。");
            suggestions.add("用“我负责 / 我实现 / 我优化”说明个人贡献，避免只写团队做了什么。");
        }

        if (containsAny(text, DEPTH_KEYWORDS)) {
            score += 5;
        } else {
            issues.add("项目技术深度表达不足。");
            suggestions.add("补充权限、缓存、异步、索引、限流、RAG、调用链路等具体技术实现。");
        }

        if (containsAny(text, DIFFICULTY_KEYWORDS)) {
            score += 4;
        } else {
            issues.add("项目缺少技术难点和解决方案。");
            suggestions.add("每个核心项目补充“遇到的问题 + 解决方案 + 最终效果”。");
        }

        if (QUANTIFIED_PATTERN.matcher(text).find()) {
            score += 3;
        } else {
            issues.add("项目缺少量化结果。");
            suggestions.add("为项目补充接口耗时、匹配准确率、调用成功率、数据量、用户量等指标。");
        }

        if (isDirectionConsistent(text, targetPosition)) {
            score += 2;
        } else if (StringUtils.hasText(targetPosition)) {
            suggestions.add("围绕目标方向“" + targetPosition + "”重新排序项目，把最相关项目放在前面。");
        }

        return dimension(
                "项目经历质量",
                score,
                25,
                score >= 20 ? "项目经历具备较好的完整度和技术说服力。" : "项目经历还需要补充技术深度、个人贡献和成果。",
                issues,
                suggestions
        );
    }

    /**
     * 实习 / 工作经历，满分 15。
     */
    private ScoreDimension scoreWorkExperience(String text, String targetPosition) {
        int score = 0;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (containsAny(text, WORK_KEYWORDS) && hasYearRange(text)) {
            score += 2;
        } else if (containsAny(text, WORK_KEYWORDS)) {
            score += 1;
            issues.add("实习或工作经历缺少清晰时间。");
            suggestions.add("工作经历建议写清公司、岗位、起止时间。");
        } else {
            issues.add("简历中未找到明确实习或工作经历。");
            suggestions.add("如果暂无实习，可以强化项目经历或开源经历来替代业务经验。");
        }

        if (containsAny(text, RESPONSIBILITY_KEYWORDS)) {
            score += 3;
        } else {
            suggestions.add("工作经历中补充职责动词，例如负责、实现、优化、排查、上线。");
        }

        if (isDirectionConsistent(text, targetPosition)) {
            score += 3;
        } else if (StringUtils.hasText(targetPosition)) {
            suggestions.add("把工作经历中与“" + targetPosition + "”相关的技术和业务职责写得更靠前。");
        }

        if (containsAny(text, BUSINESS_CONTRIBUTION_KEYWORDS)) {
            score += 3;
        } else {
            suggestions.add("补充业务贡献，例如上线模块、提升效率、降低成本、改善稳定性。");
        }

        if (containsAny(text, FRAMEWORK_KEYWORDS) || containsAny(text, DATABASE_MIDDLEWARE_KEYWORDS)) {
            score += 2;
        } else {
            issues.add("实习或工作经历中技术细节不足。");
            suggestions.add("说明在工作中使用了哪些框架、中间件和数据库。");
        }

        if (QUANTIFIED_PATTERN.matcher(text).find()) {
            score += 2;
        } else {
            suggestions.add("工作经历建议补充量化结果，例如接口耗时、处理数据量、效率提升比例。");
        }

        return dimension(
                "实习 / 工作经历",
                score,
                15,
                score >= 12 ? "实习或工作经历对求职竞争力有较好支撑。" : "实习或工作经历的业务贡献和技术证据还可以增强。",
                issues,
                suggestions
        );
    }

    /**
     * 成果量化程度，满分 10。
     */
    private ScoreDimension scoreQuantifiedImpact(String text) {
        int score = 0;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (containsAny(text, List.of("ms", "QPS", "TPS", "响应时间", "吞吐量", "性能"))) {
            score += 2;
        } else {
            suggestions.add("补充性能指标，例如接口响应时间、QPS、任务耗时。");
        }

        if (containsAny(text, List.of("用户", "订单", "业务", "转化", "营收", "访问", "请求"))) {
            score += 2;
        } else {
            suggestions.add("补充业务指标，例如用户量、订单量、请求量、业务效率提升。");
        }

        if (containsAny(text, List.of("万", "千", "k", "K", "条", "次", "人", "数据量"))) {
            score += 2;
        } else {
            suggestions.add("补充规模指标，例如处理多少条数据、支持多少用户或调用多少次。");
        }

        if (containsAny(text, List.of("%", "准确率", "成功率", "覆盖率", "错误率", "召回率"))) {
            score += 2;
        } else {
            suggestions.add("补充比例指标，例如准确率、成功率、错误率、测试覆盖率。");
        }

        if (containsAny(text, List.of("提升", "降低", "减少", "优化", "增长", "从", "到"))) {
            score += 2;
        } else {
            suggestions.add("用“从 A 到 B”的前后对比表达优化效果。");
        }

        if (score < 5) {
            issues.add("简历量化成果不足，项目说服力会被削弱。");
        }

        return dimension(
                "成果量化程度",
                score,
                10,
                score >= 7 ? "简历已经包含一定量化结果。" : "简历中的结果更多是主观描述，缺少数字支撑。",
                issues,
                suggestions
        );
    }

    /**
     * 表达与排版，满分 5。
     */
    private ScoreDimension scoreFormat(String text) {
        int score = 0;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (containsAny(text, List.of("教育", "技能", "项目", "经历", "工作", "实习")) && text.contains("\n")) {
            score += 1;
        } else {
            issues.add("简历结构层级不够清晰。");
            suggestions.add("使用清晰标题分区: 基础信息、教育背景、技能、项目经历、工作经历。");
        }

        if (!hasObviousTextNoise(text)) {
            score += 1;
        } else {
            issues.add("文本中存在疑似乱码或解析噪声。");
            suggestions.add("避免复杂图片、扫描件和不可复制文本，提升 ATS 可解析性。");
        }

        if (containsAny(text, PROFESSIONAL_VERBS)) {
            score += 1;
        } else {
            suggestions.add("用专业动词描述经历，例如负责、实现、优化、排查、上线。");
        }

        if (text.length() >= 800 && text.length() <= 20000) {
            score += 1;
        } else if (text.length() < 800) {
            issues.add("简历内容偏短，难以判断能力。");
            suggestions.add("补充项目细节、技术难点和量化结果，让简历信息量更充分。");
        } else {
            issues.add("简历文本过长，可能存在重复或重点不突出。");
            suggestions.add("压缩重复描述，把核心项目和高价值经历放在前半部分。");
        }

        if (!containsAny(text, List.of("image1.png", "word/media", ".png", ".jpg", ".jpeg"))) {
            score += 1;
        } else {
            issues.add("简历中疑似存在图片占位符，可能影响 ATS 解析。");
            suggestions.add("把关键文字从图片中提取出来，直接写进简历正文。");
        }

        return dimension(
                "表达与排版",
                score,
                5,
                score >= 4 ? "简历表达和结构整体可读。" : "简历表达、结构或 ATS 友好度仍需优化。",
                issues,
                suggestions
        );
    }

    private ScoreBreakdown buildBreakdown(List<ScoreDimension> dimensions) {
        ScoreBreakdown breakdown = new ScoreBreakdown();
        breakdown.setBasicInfoScore(dimensions.get(0).getScore());
        breakdown.setCareerGoalScore(dimensions.get(1).getScore());
        breakdown.setEducationScore(dimensions.get(2).getScore());
        breakdown.setSkillsScore(dimensions.get(3).getScore());
        breakdown.setProjectExperienceScore(dimensions.get(4).getScore());
        breakdown.setWorkExperienceScore(dimensions.get(5).getScore());
        breakdown.setQuantifiedImpactScore(dimensions.get(6).getScore());
        breakdown.setFormatScore(dimensions.get(7).getScore());
        return breakdown;
    }

    private ScoreDimension dimension(String name, int score, int maxScore, String reason, List<String> issues, List<String> suggestions) {
        ScoreDimension dimension = new ScoreDimension();
        dimension.setDimensionName(name);
        dimension.setScore(Math.max(0, Math.min(score, maxScore)));
        dimension.setMaxScore(maxScore);
        dimension.setReason(reason);
        dimension.setIssues(cleanList(issues));
        dimension.setSuggestions(cleanList(suggestions));
        return dimension;
    }

    private List<String> buildStrengths(List<ScoreDimension> dimensions, String targetPosition) {
        List<String> strengths = new ArrayList<>();

        for (ScoreDimension dimension : dimensions) {
            double ratio = dimension.getScore() * 1.0 / dimension.getMaxScore();
            if (ratio < 0.8) {
                continue;
            }

            switch (dimension.getDimensionName()) {
                case "基础信息完整性" -> strengths.add("基础信息较完整，HR 可以快速识别候选人和联系方式。");
                case "求职目标清晰度" -> strengths.add("求职方向较清晰，技能和经历能围绕目标方向展开。");
                case "教育背景" -> strengths.add("教育背景信息较完整，能够支撑候选人的基础学习经历。");
                case "技能结构" -> strengths.add("技能结构覆盖核心语言、框架和工程化能力，具备较好的技术基础。");
                case "项目经历质量" -> strengths.add("项目经历较完整，能够体现一定技术实现和个人贡献。");
                case "实习 / 工作经历" -> strengths.add("实习或工作经历能够体现真实业务场景下的开发经验。");
                case "成果量化程度" -> strengths.add("简历中已经出现量化结果，项目成果更容易被招聘方理解。");
                case "表达与排版" -> strengths.add("简历结构和表达较清晰，具备较好的阅读体验。");
                default -> {
                }
            }
        }

        if (StringUtils.hasText(targetPosition)) {
            strengths.add("本次评分已结合用户填写的求职方向“" + targetPosition + "”检查简历主线是否一致。");
        }

        if (strengths.isEmpty()) {
            strengths.add("简历已经具备基础内容，可以在项目深度、技能证据和量化成果上继续增强。");
        }

        return strengths.stream().distinct().limit(5).toList();
    }

    private List<String> buildWeaknesses(List<ScoreDimension> dimensions) {
        List<String> weaknesses = dimensions.stream()
                .flatMap(dimension -> dimension.getIssues().stream())
                .distinct()
                .limit(6)
                .toList();

        if (weaknesses.isEmpty()) {
            return List.of("暂无明显问题。");
        }
        return weaknesses;
    }

    private List<String> buildRiskPoints(List<ScoreDimension> dimensions) {
        List<String> risks = new ArrayList<>();
        ScoreDimension career = dimensions.get(1);
        ScoreDimension skills = dimensions.get(3);
        ScoreDimension project = dimensions.get(4);
        ScoreDimension quantified = dimensions.get(6);
        ScoreDimension format = dimensions.get(7);

        if (career.getScore() < 6) {
            risks.add("求职方向不够聚焦，可能导致 HR 不清楚这份简历适合投递什么岗位。");
        }
        if (skills.getScore() >= 10 && project.getScore() < 15) {
            risks.add("技能栈写得不少，但项目经历中的证据不足，容易被认为是关键词堆砌。");
        }
        if (project.getScore() < 15) {
            risks.add("项目经历说服力不足，会直接影响技术面试官对实战能力的判断。");
        }
        if (quantified.getScore() < 5) {
            risks.add("缺少量化成果，简历竞争力会弱于能提供数据结果的候选人。");
        }
        if (format.getScore() < 3) {
            risks.add("排版或解析噪声可能影响 ATS 自动识别。");
        }

        if (risks.isEmpty()) {
            risks.add("暂无明显高风险点，后续主要做针对岗位的项目排序和表达优化。");
        }
        return risks;
    }

    private List<String> buildImprovementSuggestions(List<ScoreDimension> dimensions) {
        Set<String> suggestions = new LinkedHashSet<>();
        for (ScoreDimension dimension : dimensions) {
            suggestions.addAll(dimension.getSuggestions());
        }

        if (suggestions.isEmpty()) {
            suggestions.add("当前简历整体较完整，建议针对不同岗位微调项目顺序和技能关键词。");
        }

        return suggestions.stream().limit(8).toList();
    }

    private String buildSummary(int total, String targetPosition) {
        String targetText = StringUtils.hasText(targetPosition) ? "，本次也参考了求职方向“" + targetPosition + "”" : "";
        if (total >= 90) {
            return "该简历整体质量优秀" + targetText + "，可以直接用于投递，只需补充少量量化成果和项目亮点。";
        }
        if (total >= 80) {
            return "该简历整体质量良好" + targetText + "，已经具备竞争力，建议继续加强项目深度和量化成果。";
        }
        if (total >= 70) {
            return "该简历基础较完整" + targetText + "，但项目技术深度、个人贡献或量化结果还需要增强。";
        }
        if (total >= 60) {
            return "该简历能看出部分经历" + targetText + "，但主线、证据和表达都需要系统优化。";
        }
        return "该简历目前信息不足或结构较弱" + targetText + "，建议按基础信息、技能、项目、经历重新组织。";
    }

    private boolean hasLikelyName(String text) {
        List<String> lines = firstLines(text, 8);
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.length() < 2 || cleaned.length() > 20) {
                continue;
            }
            if (containsAny(cleaned, List.of("电话", "邮箱", "求职", "教育", "技能", "项目", "http", "@"))) {
                continue;
            }
            if (cleaned.matches(".*[\\u4e00-\\u9fa5]{2,4}.*") || cleaned.matches("[A-Z][a-zA-Z]+\\s+[A-Z][a-zA-Z]+")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasYearRange(String text) {
        return text.matches("(?s).*20\\d{2}.*(20\\d{2}|至今|现在|Present).*");
    }

    private boolean hasObviousTextNoise(String text) {
        return containsAny(text, List.of("锟斤拷", "����", "解析失败", "word/media", "image1.png", "乱码"));
    }

    private boolean isDirectionConsistent(String text, String targetPosition) {
        String lowerTarget = targetPosition == null ? "" : targetPosition.toLowerCase(Locale.ROOT);

        if (lowerTarget.contains("agent") || lowerTarget.contains("rag") || lowerTarget.contains("ai") || lowerTarget.contains("大模型")) {
            return containsAny(text, AI_AGENT_KEYWORDS);
        }
        if (lowerTarget.contains("java") || lowerTarget.contains("后端") || lowerTarget.contains("backend")) {
            return containsAny(text, List.of("Java", "Spring", "MyBatis", "MySQL", "Redis", "后端", "接口", "微服务"));
        }
        if (lowerTarget.contains("前端") || lowerTarget.contains("frontend")) {
            return containsAny(text, List.of("Vue", "React", "TypeScript", "JavaScript", "前端", "HTML", "CSS"));
        }
        if (lowerTarget.contains("全栈") || lowerTarget.contains("full")) {
            return containsAny(text, List.of("Vue", "React", "Spring", "Java", "Node", "全栈"));
        }

        /*
         * 用户没有填写目标方向时，用“是否形成明显技术簇”来判断。
         * 例如 Java + Spring + MySQL + Redis 共同出现，就说明简历主线大概率是后端。
         */
        int backendHits = countKeywordHits(text, List.of("Java", "Spring", "MyBatis", "MySQL", "Redis", "后端"));
        int aiHits = countKeywordHits(text, AI_AGENT_KEYWORDS);
        int frontendHits = countKeywordHits(text, List.of("Vue", "React", "TypeScript", "JavaScript", "前端"));
        return backendHits >= 3 || aiHits >= 2 || frontendHits >= 3;
    }

    private int countKeywordHits(String text, List<String> keywords) {
        int count = 0;
        String lowerText = text.toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> firstLines(String text, int limit) {
        String[] lines = text.split("\\R+");
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.isBlank()) {
                result.add(line.trim());
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<String> cleanList(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ')
                .trim();
    }

    private String resolveLevel(int score) {
        if (score >= 90) {
            return "优秀";
        }
        if (score >= 80) {
            return "良好";
        }
        if (score >= 70) {
            return "一般";
        }
        if (score >= 60) {
            return "较弱";
        }
        return "需要重写";
    }

    /**
     * V2 最终评分结构。
     * 说明: 这个结构会被保存到 score_json，也会作为 LLM 合并时的中间对象。
     */
    @Data
    public static class RuleScoreResult {
        private String scoreVersion;
        private String scoringMode;
        private Integer overallScore;
        private String level;
        private ScoreBreakdown scoreBreakdown;
        private List<ScoreDimension> dimensions = List.of();
        private List<String> strengths = List.of();
        private List<String> weaknesses = List.of();
        private List<String> riskPoints = List.of();
        private List<String> improvementSuggestions = List.of();
        private String summary;
        private String llmStatus;
        private String llmError;
    }

    /**
     * V2 八个评分维度的分数明细。
     */
    @Data
    public static class ScoreBreakdown {
        private Integer basicInfoScore;
        private Integer careerGoalScore;
        private Integer educationScore;
        private Integer skillsScore;
        private Integer projectExperienceScore;
        private Integer workExperienceScore;
        private Integer quantifiedImpactScore;
        private Integer formatScore;
    }

    /**
     * 单个维度的解释信息。
     */
    @Data
    public static class ScoreDimension {
        private String dimensionName;
        private Integer score;
        private Integer maxScore;
        private String reason;
        private List<String> issues = List.of();
        private List<String> suggestions = List.of();
    }
}
