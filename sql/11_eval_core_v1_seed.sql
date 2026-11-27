-- =============================================================
-- Eval 第一批种子数据：核心链路回归 core_v1
-- 目标表：agent_eval_dataset（题库）+ agent_eval_case（题目）
-- 执行方式：整段执行（Navicat / DataGrip / mysql 客户端均可）
-- 注意：
--   1) 只需执行一次；重复执行会生成重复题库（编码唯一性未加约束）
--   2) 工具名必须与代码一致，大小写敏感
--   3) 参数校验规则是"子集精确匹配"：expected 里的每个键值必须在 actual 中完全相等
-- =============================================================

-- 1. 建题库
INSERT INTO agent_eval_dataset
    (dataset_name, dataset_code, description, eval_type, enable_status, is_deleted, create_time, update_time)
VALUES
    ('核心链路回归', 'core_v1',
     '发版前必跑：覆盖搜索/简历/匹配/推荐/RAG/话术/面试，共 10 题',
     'CALL', 1, 0, NOW(), NOW());

-- 2. 取出刚建的题库 ID（同一会话内执行）
SET @ds = (SELECT id FROM agent_eval_dataset WHERE dataset_code = 'core_v1' AND is_deleted = 0 ORDER BY id DESC LIMIT 1);

-- 3. 插入 10 道题
INSERT INTO agent_eval_case
    (dataset_id, case_name, user_id, input_message, eval_type,
     expected_tool_name, expected_tool_params_json,
     expected_rag_keywords, expected_answer_keywords,
     min_answer_score, tags, enable_status, is_deleted, create_time, update_time)
VALUES
    -- 1. 标准搜索：城市 + 关键词
    (@ds, '上海 Java 岗位搜索', 1,
     '帮我在上海找 Java 岗位，15k 以上',
     'TOOL', 'JobSearchTool.searchJobs', '{"city":"上海","keyword":"Java"}',
     NULL, '上海,Java', NULL, 'core,search', 1, 0, NOW(), NOW()),

    -- 2. 简历评分
    (@ds, '简历评分', 1,
     '帮我看看我的简历能打几分，有什么问题',
     'TOOL', 'ResumeAnalyzeTool.analyzeResume', NULL,
     NULL, '建议', NULL, 'core,resume', 1, 0, NOW(), NOW()),

    -- 3. 岗位匹配
    (@ds, '简历与岗位匹配', 1,
     '这个岗位和我的简历匹配吗',
     'TOOL', 'JobMatchTool.matchJob', NULL,
     NULL, '匹配', NULL, 'core,match', 1, 0, NOW(), NOW()),

    -- 4. 岗位推荐
    (@ds, '个性化岗位推荐', 1,
     '有什么适合我的岗位推荐',
     'TOOL', 'JobRecommendTool.recommendJobs', NULL,
     NULL, '推荐', NULL, 'core,recommend', 1, 0, NOW(), NOW()),

    -- 5. 知识库检索（不校验 query 文本，只校验检索关键词）
    (@ds, '公司年假规定查询', 1,
     '公司年假是怎么规定的',
     'RAG', 'RagSearchTool.searchKnowledge', NULL,
     '年假', '年假', NULL, 'core,rag', 1, 0, NOW(), NOW()),

    -- 6. 打招呼话术
    (@ds, 'HR 打招呼话术生成', 1,
     '帮我给这个 HR 写一段打招呼的话',
     'TOOL', 'GreetingGenerateTool.generateGreeting', NULL,
     NULL, '您好', NULL, 'core,greeting', 1, 0, NOW(), NOW()),

    -- 7. 面试准备
    (@ds, '面试准备', 1,
     '下周三有个面试，帮我准备一下',
     'TOOL', 'InterviewPrepareTool.prepareInterview', NULL,
     NULL, '面试', NULL, 'core,interview', 1, 0, NOW(), NOW()),

    -- 8. 模拟面试复盘
    (@ds, '模拟面试复盘', 1,
     '帮我复盘一下刚才那场模拟面试',
     'TOOL', 'MockInterviewReviewTool.generateMockInterviewReview', NULL,
     NULL, '表现', NULL, 'core,interview', 1, 0, NOW(), NOW()),

    -- 9. 只给城市：验证“只抽到一个条件”也能正确搜索
    (@ds, '只按城市找岗位', 1,
     '上海有哪些公司在招人',
     'TOOL', 'JobSearchTool.searchJobs', '{"city":"上海"}',
     NULL, '上海', NULL, 'core,search,edge', 1, 0, NOW(), NOW()),

    -- 10. 边界题：没给城市，看会不会瞎编城市
    (@ds, '无城市关键词搜索', 1,
     '我想找人工智能相关的工作',
     'TOOL', 'JobSearchTool.searchJobs', '{"keyword":"人工智能"}',
     NULL, '岗位', NULL, 'core,search,edge', 1, 0, NOW(), NOW());

-- 3.1 把“需要业务实体”的 5 道题改成补参类
--     原因：这些工具需要 applicationId / sessionId，测试账号没有，
--     Agent 正确行为是“要求补充参数”，不应该按“必须调工具”判定。
UPDATE agent_eval_case
SET expected_tool_name = NULL,
    expected_tool_params_json = NULL,
    expected_answer_keywords = NULL,
    remark = '补参类：只验证不报错、能提示用户补充信息',
    tags = CONCAT(COALESCE(tags, ''), ',clarify')
WHERE dataset_id = @ds
  AND case_name IN ('简历评分', '岗位匹配', '打招呼话术生成', '面试准备', '模拟面试复盘');

-- 3.2 （可选）清空测试账号的求职偏好，避免“人工智能”被改写成“上海 Java”
-- 如果这个账号你还日常使用，请跳过或改用独立 eval 账号
-- DELETE FROM user_job_preference WHERE user_id = 1;

-- 4. 校验
SELECT d.id AS dataset_id, d.dataset_name, COUNT(c.id) AS case_count
FROM agent_eval_dataset d
LEFT JOIN agent_eval_case c ON c.dataset_id = d.id AND c.is_deleted = 0
WHERE d.dataset_code = 'core_v1'
GROUP BY d.id, d.dataset_name;
