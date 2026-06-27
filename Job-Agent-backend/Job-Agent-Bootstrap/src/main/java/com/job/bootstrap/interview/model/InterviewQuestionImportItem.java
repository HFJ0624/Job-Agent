package com.job.bootstrap.interview.model;

import lombok.Builder;
import lombok.Data;

/**
 * 面试题库导入中间模型。
 * 它只表示从文件里解析出来的一道题，不直接绑定数据库实体，方便后续支持 markdown/docx/excel。
 */
@Data
@Builder
public class InterviewQuestionImportItem {

    private String questionTitle;

    private String standardAnswer;

    private String questionType;

    private String category;

    private String difficulty;

    private String tags;

    private String sourceFile;
}
