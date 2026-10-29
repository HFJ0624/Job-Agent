package com.job.bootstrap.agent.plan;

import com.job.bootstrap.agent.intent.AgentIntentCode;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentPlanTemplateFactory 单元测试。
 *
 * 覆盖重点（任务 J1 主流程）:
 * 1. JOB_SEEK_FLOW 生成四步主流程：简历分析 → 岗位筛选 → Top1 匹配 → 投递话术；
 * 2. requiredParams 只含 resumeId（缺它走澄清，keyword/city 可选）；
 * 3. S3/S4 声明步骤级参数，jobId 用 {{step_2.output.jobs[0].jobId}} 数组下标占位符；
 * 4. 其余意图模板行为不回退（单目标意图仍是两步结构）。
 */
class AgentPlanTemplateFactoryTest {

    private final AgentToolSchemaRegistry registry = new AgentToolSchemaRegistry();
    private AgentPlanTemplateFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AgentPlanTemplateFactory(registry);
    }

    @Test
    void jobSeekFlowShouldChainFourSteps() {
        AgentPlanTemplate template = factory.create(AgentIntentCode.JOB_SEEK_FLOW, "帮我走完整求职流程");

        assertThat(template.planTitle()).isEqualTo("求职主流程计划");
        assertThat(template.requiredParams()).containsExactly("resumeId");
        assertThat(template.steps()).hasSize(4);
        assertThat(template.steps().get(0).toolName()).isEqualTo("ResumeAnalyzeTool.analyzeResume");
        assertThat(template.steps().get(1).toolName())
                .isEqualTo("JobSearchTool.searchJobs / JobRecommendTool.recommendJobs");
        assertThat(template.steps().get(2).toolName()).isEqualTo("JobMatchTool.matchJob");
        assertThat(template.steps().get(3).toolName()).isEqualTo("GreetingGenerateTool.generateGreeting");
    }

    @Test
    void matchAndGreetingStepsShouldCarryIndexedPlaceholder() {
        AgentPlanTemplate template = factory.create(AgentIntentCode.JOB_SEEK_FLOW, "完整流程");

        AgentPlanStepTemplate matchStep = template.steps().get(2);
        AgentPlanStepTemplate greetingStep = template.steps().get(3);

        // jobId 从第 2 步岗位列表取第一个（数组下标占位符）
        assertThat(matchStep.stepParams()).containsEntry("jobId", "{{step_2.output.jobs[0].jobId}}");
        assertThat(greetingStep.stepParams()).containsEntry("jobId", "{{step_2.output.jobs[0].jobId}}");
        // 前两步不声明步骤级参数（沿用计划级 extractedParams）
        assertThat(template.steps().get(0).stepParams()).isNull();
        assertThat(template.steps().get(1).stepParams()).isNull();
    }

    @Test
    void singleGoalTemplatesShouldRemainUnchanged() {
        AgentPlanTemplate resumeTemplate = factory.create(AgentIntentCode.RESUME_ANALYZE, "分析简历");

        assertThat(resumeTemplate.steps()).hasSize(2);
        assertThat(resumeTemplate.steps().get(1).toolName()).isEqualTo("ResumeAnalyzeTool.analyzeResume");
        // 旧模板无步骤级参数，行为与 J1 之前一致
        assertThat(resumeTemplate.steps().get(1).stepParams()).isNull();
    }

    @Test
    void everyStepShouldDeclareCompletionCriteria() {
        AgentPlanTemplate template = factory.create(AgentIntentCode.JOB_SEEK_FLOW, "完整流程");

        for (AgentPlanStepTemplate step : template.steps()) {
            assertThat(step.completionCriteria()).isNotBlank();
            assertThat(step.stepGoal()).isNotBlank();
        }
    }
}
