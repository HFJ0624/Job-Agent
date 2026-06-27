package com.job.bootstrap.interview.parser;

import com.job.bootstrap.interview.model.InterviewQuestionImportItem;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 面试题解析器。
 * 第一版采用保守规则：二级及更深标题作为题目，标题下方到下一个标题之间作为标准答案。
 */
@Component
public class InterviewQuestionMarkdownParser {

    private static final String DEFAULT_TYPE = "TECHNICAL";
    private static final String DEFAULT_DIFFICULTY = "MEDIUM";

    public List<InterviewQuestionImportItem> parse(String sourceFile, String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }

        List<InterviewQuestionImportItem> items = new ArrayList<>();
        String currentCategory = null;
        String currentQuestion = null;
        StringBuilder answer = new StringBuilder();

        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                flush(items, sourceFile, currentCategory, currentQuestion, answer);
                currentCategory = cleanHeading(trimmed);
                currentQuestion = null;
                answer = new StringBuilder();
                continue;
            }

            if (trimmed.matches("^#{2,6}\\s+.+")) {
                flush(items, sourceFile, currentCategory, currentQuestion, answer);
                currentQuestion = cleanHeading(trimmed);
                answer = new StringBuilder();
                continue;
            }

            if (currentQuestion != null) {
                answer.append(line).append('\n');
            }
        }

        flush(items, sourceFile, currentCategory, currentQuestion, answer);
        return items;
    }

    private void flush(
            List<InterviewQuestionImportItem> items,
            String sourceFile,
            String category,
            String question,
            StringBuilder answer
    ) {
        String answerText = answer == null ? "" : answer.toString().trim();
        if (!StringUtils.hasText(question) || answerText.length() < 10) {
            return;
        }

        /*
         * 1. 只保存有答案正文的标题，避免把章节标题误当题目。
         * 2. category 使用最近的一级标题，便于后台筛选 Java/Spring/AI 应用等主题。
         * 3. tags 第一版先放分类名，后续可扩展为模型自动抽取知识点。
         */
        items.add(InterviewQuestionImportItem.builder()
                .questionTitle(question.trim())
                .standardAnswer(answerText)
                .questionType(DEFAULT_TYPE)
                .category(StringUtils.hasText(category) ? category.trim() : null)
                .difficulty(DEFAULT_DIFFICULTY)
                .tags(StringUtils.hasText(category) ? category.trim() : null)
                .sourceFile(sourceFile)
                .build());
    }

    private String cleanHeading(String heading) {
        return heading.replaceFirst("^#+\\s+", "").trim();
    }
}
