package com.job.common.vo.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.interview.MockInterviewStudyPlan;
import com.job.common.entity.interview.MockInterviewStudyPlanItem;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 模拟面试学习计划详情 VO。
 */
@Data
public class MockInterviewLearningPlanVO {

    private Long id;
    private String planTitle;
    private Integer planDays;
    private String source;
    private List<String> weakKnowledgePoints;
    private String status;
    private String createTime;
    private List<Item> items = new ArrayList<>();

    public static MockInterviewLearningPlanVO from(
            MockInterviewStudyPlan plan,
            List<MockInterviewStudyPlanItem> items,
            ObjectMapper objectMapper
    ) {
        if (plan == null) {
            return null;
        }

        MockInterviewLearningPlanVO vo = new MockInterviewLearningPlanVO();
        vo.setId(plan.getId());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setPlanDays(plan.getPlanDays());
        vo.setSource(plan.getSource());
        vo.setWeakKnowledgePoints(splitLines(plan.getWeakKnowledgePoints()));
        vo.setStatus(plan.getStatus());
        vo.setCreateTime(plan.getCreateTime() == null ? null : plan.getCreateTime().toString());
        vo.setItems(items == null
                ? Collections.emptyList()
                : items.stream().map(item -> Item.from(item, objectMapper)).toList());
        return vo;
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split("\\R+"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    @Data
    public static class Item {
        private Long id;
        private Integer dayNo;
        private String title;
        private String knowledgePoint;
        private String learningGoal;
        private String practiceTask;
        private String reviewSuggestion;
        private String completionStatus;
        private List<Material> materials;

        public static Item from(MockInterviewStudyPlanItem entity, ObjectMapper objectMapper) {
            Item item = new Item();
            item.setId(entity.getId());
            item.setDayNo(entity.getDayNo());
            item.setTitle(entity.getTitle());
            item.setKnowledgePoint(entity.getKnowledgePoint());
            item.setLearningGoal(entity.getLearningGoal());
            item.setPracticeTask(entity.getPracticeTask());
            item.setReviewSuggestion(entity.getReviewSuggestion());
            item.setCompletionStatus(entity.getCompletionStatus());
            item.setMaterials(readMaterials(entity.getMaterialsJson(), objectMapper));
            return item;
        }

        private static List<Material> readMaterials(String json, ObjectMapper objectMapper) {
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            try {
                return objectMapper.readValue(json, new TypeReference<List<Material>>() {
                });
            } catch (Exception exception) {
                return Collections.emptyList();
            }
        }
    }

    @Data
    public static class Material {
        private Long documentId;
        private Long chunkId;
        private String title;
        private String content;
        private String source;
        private Double score;
    }
}
