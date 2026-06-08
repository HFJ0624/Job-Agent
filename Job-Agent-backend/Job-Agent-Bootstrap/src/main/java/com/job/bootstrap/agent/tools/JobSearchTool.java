package com.job.bootstrap.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.JobPositionService;
import com.job.common.entity.position.JobPosition;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
/**
 * 作者:hfj
 * 功能:岗位搜索工具
 * 日期: 2026/6/8 15:15
 */
@Component
@RequiredArgsConstructor
public class JobSearchTool {

    private final JobPositionService jobPositionService;
    private final ObjectMapper objectMapper;

    @Tool("根据城市、关键词和最低薪资搜索岗位，返回最多10个岗位")
    public String searchJobs(
            @P("岗位关键词，例如 Java、后端、前端、AI应用开发") String keyword,
            @P("城市，例如 上海、杭州、北京，可以为空") String city,
            @P("最低薪资，单位元，可以为空") Integer minSalary
    ) {
        try {
            LambdaQueryWrapper<JobPosition> wrapper = new LambdaQueryWrapper<>();

            /*
             * 关键词模糊匹配岗位名称、岗位描述、技能关键词。
             */
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w
                        .like(JobPosition::getJobTitle, keyword)
                        .or()
                        .like(JobPosition::getJobDescription, keyword)
                        .or()
                        .like(JobPosition::getSkillKeywords, keyword)
                );
            }

            /*
             * 城市精确匹配。
             */
            if (StringUtils.hasText(city)) {
                wrapper.eq(JobPosition::getCity, city);
            }

            /*
             * 薪资筛选。
             */
            if (minSalary != null && minSalary > 0) {
                wrapper.ge(JobPosition::getMaxSalary, minSalary);
            }

            wrapper.orderByDesc(JobPosition::getPublishTime)
                    .last("limit 10");

            List<JobPosition> jobs = jobPositionService.list(wrapper);
            return objectMapper.writeValueAsString(jobs);
        } catch (Exception e) {
            return "岗位搜索失败：" + e.getMessage();
        }
    }
}
