package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.MockInterviewWrongQuestionMapper;
import com.job.bootstrap.service.MockInterviewWrongQuestionService;
import com.job.common.dto.interview.MockInterviewWrongQuestionQueryDTO;
import com.job.common.dto.interview.MockInterviewWrongQuestionStatusDTO;
import com.job.common.entity.interview.MockInterviewWrongQuestion;
import com.job.common.vo.interview.MockInterviewWrongQuestionVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模拟面试错题本服务实现。
 *
 * <p>核心职责：管理用户模拟面试过程中产生的错题，支持分页查询、掌握状态更新及薄弱知识点提取。
 *
 * <p>所属业务模块：面试训练中心 - 错题本（Mock Interview Wrong Question）。
 *
 * <p>主要调用链：
 * <ul>
 *   <li>错题查询：{@code pageWrongQuestions} → 分页返回错题列表。</li>
 *   <li>状态管理：{@code updateMasteryStatus} → 更新错题掌握状态（UNMASTERED / REVIEWING / MASTERED）。</li>
 *   <li>知识点提取：{@code listActiveWeakKnowledgePoints} → 按错误次数倒序返回薄弱知识点。</li>
 * </ul>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>{@link MockInterviewWrongQuestionMapper}：错题本数据持久化操作。</li>
 *   <li>{@link MockInterviewServiceImpl}：答题评分后自动调用错题沉淀逻辑。</li>
 *   <li>{@link MockInterviewLearningPlanServiceImpl}：基于错题本数据生成个性化学习计划。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>掌握状态三态机：UNMASTERED（未掌握）→ REVIEWING（复习中）→ MASTERED（已掌握），复测通过自动流转。</li>
 *   <li>错题按错误次数和更新时间排序，高频错题优先展示，便于用户聚焦重点。</li>
 *   <li>薄弱知识点从错题的 knowledgePoints 字段提取，去重后供学习计划和题目选择服务消费。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MockInterviewWrongQuestionServiceImpl implements MockInterviewWrongQuestionService {

    private static final int NOT_DELETED = 0;
    private static final String UNMASTERED = "UNMASTERED";
    private static final String REVIEWING = "REVIEWING";
    private static final String MASTERED = "MASTERED";

    private final MockInterviewWrongQuestionMapper wrongQuestionMapper;

    /**
     * 分页查询用户错题本。
     *
     * <p>支持按掌握状态过滤及多字段关键词搜索（题目内容、知识点、缺失要点、建议）。
     *
     * @param userId 用户 ID
     * @param query  查询条件
     * @return 分页后的错题 VO 列表
     */
    @Override
    public IPage<MockInterviewWrongQuestionVO> pageWrongQuestions(Long userId, MockInterviewWrongQuestionQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() <= 0 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 10 : Math.min(query.getPageSize(), 100);

        LambdaQueryWrapper<MockInterviewWrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MockInterviewWrongQuestion::getUserId, userId)
                .eq(MockInterviewWrongQuestion::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(query.getMasteryStatus())) {
            wrapper.eq(MockInterviewWrongQuestion::getMasteryStatus, normalizeStatus(query.getMasteryStatus()));
        }

        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item
                    .like(MockInterviewWrongQuestion::getQuestionContent, keyword)
                    .or()
                    .like(MockInterviewWrongQuestion::getKnowledgePoints, keyword)
                    .or()
                    .like(MockInterviewWrongQuestion::getMissingPoints, keyword)
                    .or()
                    .like(MockInterviewWrongQuestion::getSuggestions, keyword));
        }

        wrapper.orderByAsc(MockInterviewWrongQuestion::getMasteryStatus)
                .orderByDesc(MockInterviewWrongQuestion::getWrongCount)
                .orderByDesc(MockInterviewWrongQuestion::getUpdateTime);

        IPage<MockInterviewWrongQuestion> page = wrongQuestionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return page.convert(MockInterviewWrongQuestionVO::from);
    }

    /**
     * 更新错题掌握状态。
     *
     * <p>支持状态：UNMASTERED（未掌握）、REVIEWING（复习中）、MASTERED（已掌握）。
     *
     * @param userId 用户 ID
     * @param id     错题记录 ID
     * @param dto    状态更新参数
     * @return 更新后的错题 VO
     */
    @Override
    public MockInterviewWrongQuestionVO updateMasteryStatus(Long userId, Long id, MockInterviewWrongQuestionStatusDTO dto) {
        MockInterviewWrongQuestion wrongQuestion = wrongQuestionMapper.selectById(id);
        if (wrongQuestion == null
                || !userId.equals(wrongQuestion.getUserId())
                || (wrongQuestion.getIsDeleted() != null && wrongQuestion.getIsDeleted() == 1)) {
            throw new BizException("错题不存在或无权限访问");
        }

        wrongQuestion.setMasteryStatus(normalizeStatus(dto.getMasteryStatus()));
        wrongQuestionMapper.updateById(wrongQuestion);
        return MockInterviewWrongQuestionVO.from(wrongQuestion);
    }

    /**
     * 提取用户活跃错题中的薄弱知识点。
     *
     * <p>仅统计 UNMASTERED 和 REVIEWING 状态的错题，按错误次数和更新时间倒序提取知识点，去重后返回。
     *
     * @param userId 用户 ID
     * @param limit  返回知识点数量上限
     * @return 薄弱知识点列表
     */
    @Override
    public List<String> listActiveWeakKnowledgePoints(Long userId, int limit) {
        List<MockInterviewWrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewWrongQuestion>()
                        .eq(MockInterviewWrongQuestion::getUserId, userId)
                        .eq(MockInterviewWrongQuestion::getIsDeleted, NOT_DELETED)
                        .in(MockInterviewWrongQuestion::getMasteryStatus, UNMASTERED, REVIEWING)
                        .orderByDesc(MockInterviewWrongQuestion::getWrongCount)
                        .orderByDesc(MockInterviewWrongQuestion::getUpdateTime)
                        .last("limit 50")
        );

        Set<String> points = new LinkedHashSet<>();
        for (MockInterviewWrongQuestion wrongQuestion : wrongQuestions) {
            splitLines(wrongQuestion.getKnowledgePoints()).forEach(points::add);
            if (points.size() >= limit) {
                break;
            }
        }
        return points.stream().limit(limit).toList();
    }

    /**
     * 规范化掌握状态值。
     *
     * @param status 原始状态字符串
     * @return 规范化后的状态码
     * @throws BizException 状态不合法时抛出
     */
    private String normalizeStatus(String status) {
        String value = status == null ? UNMASTERED : status.trim().toUpperCase();
        if (!List.of(UNMASTERED, REVIEWING, MASTERED).contains(value)) {
            throw new BizException("不支持的掌握状态：" + value);
        }
        return value;
    }

    /**
     * 多行文本拆分为列表。
     *
     * @param value 多行文本
     * @return 非空行列表
     */
    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
