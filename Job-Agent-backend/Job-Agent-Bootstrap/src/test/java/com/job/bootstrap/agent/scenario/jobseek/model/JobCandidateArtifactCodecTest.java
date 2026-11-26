package com.job.bootstrap.agent.scenario.jobseek.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.scenario.jobsearch.JobCandidates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审查 F4 定点验证：候选产物必须按保存端的 {"jobId","raw"} 结构解回真实岗位字段。
 */
class JobCandidateArtifactCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void wrappedStructureShouldKeepJobFields() {
        String json = """
                [{"jobId":21,"raw":{"jobTitle":"Java 后端开发","city":"上海","minSalary":20000,"maxSalary":30000}}]
                """;

        List<JobCandidates.JobCandidateItem> items = JobCandidateArtifactCodec.decode(json, mapper);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).jobId()).isEqualTo(21L);
        // 修复前这里会取到外层对象，city/salary 全为空
        assertThat(items.get(0).raw().get("city")).isEqualTo("上海");
        assertThat(items.get(0).raw().get("maxSalary")).isEqualTo(30000);
    }

    @Test
    void flatStructureShouldStillWork() {
        String json = """
                [{"jobId":22,"jobTitle":"Go 后端","city":"杭州"}]
                """;

        List<JobCandidates.JobCandidateItem> items = JobCandidateArtifactCodec.decode(json, mapper);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).raw().get("city")).isEqualTo("杭州");
    }
}
