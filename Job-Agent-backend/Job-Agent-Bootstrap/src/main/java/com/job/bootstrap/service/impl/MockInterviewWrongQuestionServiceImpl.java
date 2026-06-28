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
 */
@Service
@RequiredArgsConstructor
public class MockInterviewWrongQuestionServiceImpl implements MockInterviewWrongQuestionService {

    private static final int NOT_DELETED = 0;
    private static final String UNMASTERED = "UNMASTERED";
    private static final String REVIEWING = "REVIEWING";
    private static final String MASTERED = "MASTERED";

    private final MockInterviewWrongQuestionMapper wrongQuestionMapper;

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

    private String normalizeStatus(String status) {
        String value = status == null ? UNMASTERED : status.trim().toUpperCase();
        if (!List.of(UNMASTERED, REVIEWING, MASTERED).contains(value)) {
            throw new BizException("不支持的掌握状态：" + value);
        }
        return value;
    }

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
