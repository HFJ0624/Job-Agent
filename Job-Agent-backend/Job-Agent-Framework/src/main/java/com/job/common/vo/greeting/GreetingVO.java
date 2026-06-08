package com.job.common.vo.greeting;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.greeting.JobGreetingRecord;
import lombok.Data;

import java.util.Collections;
import java.util.Date;
import java.util.List;
/**
 * 作者:hfj
 * 功能:HR 打招呼语返回结果
 * 日期: 2026/6/8 13:58
 */
@Data
public class GreetingVO {

    private Long id;
    private Long userId;
    private Long resumeId;
    private Long jobId;

    private String style;
    private String content;
    private List<String> matchedSkills;
    private String source;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Entity 转 VO。
     */
    public static GreetingVO from(JobGreetingRecord record, ObjectMapper objectMapper) {
        if (record == null) {
            return null;
        }

        GreetingVO vo = new GreetingVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setResumeId(record.getResumeId());
        vo.setJobId(record.getJobId());
        vo.setStyle(record.getStyle());
        vo.setContent(record.getContent());
        vo.setSource(record.getSource());
        vo.setCreateTime(record.getCreateTime());
        vo.setMatchedSkills(readStringList(record.getMatchedSkills(), objectMapper));
        return vo;
    }

    /**
     * JSON 字符串转技能列表。
     */
    private static List<String> readStringList(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
