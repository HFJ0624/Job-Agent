package com.job.common.vo.interview;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 面试题库导入结果。
 */
@Data
public class InterviewQuestionImportResultVO {

    private Integer scannedFileCount = 0;

    private Integer parsedQuestionCount = 0;

    private Integer insertedCount = 0;

    private Integer updatedCount = 0;

    private Integer indexedCount = 0;

    private Integer failedCount = 0;

    private List<String> warnings = new ArrayList<>();

    public void addScannedFile() {
        scannedFileCount++;
    }

    public void addParsedQuestion() {
        parsedQuestionCount++;
    }

    public void addInserted() {
        insertedCount++;
    }

    public void addUpdated() {
        updatedCount++;
    }

    public void addIndexed() {
        indexedCount++;
    }

    public void addFailed(String message) {
        failedCount++;
        warnings.add(message);
    }
}
