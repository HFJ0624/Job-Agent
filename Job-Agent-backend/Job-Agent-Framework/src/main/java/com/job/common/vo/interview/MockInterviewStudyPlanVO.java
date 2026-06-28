package com.job.common.vo.interview;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能: 模拟面试复盘后的补课清单。
 *
 * 说明:
 * 1. 第一版不新建学习计划表，直接根据最新复盘报告动态生成。
 * 2. items 按薄弱知识点组织，每个知识点挂载若干 RAG 学习材料。
 */
@Data
public class MockInterviewStudyPlanVO {

    private Long sessionId;

    private Long reviewId;

    private List<StudyItem> items = new ArrayList<>();

    @Data
    public static class StudyItem {

        /**
         * 需要补充的知识点或薄弱题。
         */
        private String knowledgePoint;

        /**
         * AI 复盘给出的练习建议。
         */
        private String suggestion;

        /**
         * RAG 召回的学习材料。
         */
        private List<StudyMaterial> materials = new ArrayList<>();
    }

    @Data
    public static class StudyMaterial {

        private Long documentId;

        private Long chunkId;

        private String title;

        private String content;

        private String source;

        private Double score;
    }
}
