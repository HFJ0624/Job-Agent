package com.job.bootstrap.agent.orchestration.seek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.domain.job.JobCandidates;
import com.job.bootstrap.agent.domain.match.MatchReport;
import com.job.bootstrap.agent.domain.resume.ResumeProfile;
import com.job.bootstrap.agent.runtime.artifact.AgentArtifactService;
import com.job.common.entity.agent.AgentArtifact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 功能: 复合任务产物读写（从 JobSeekWorkflow 抽出）。
 *
 * <p>边界：只负责"按类型 + 版本存取结构化产物"和旧格式兼容，
 * **不决定**什么时候保存/复用（那属于 Workflow 的编排决策）。</p>
 *
 * <p>关键约定：</p>
 * <ul>
 *   <li>保存幂等：同 (计划, 类型, 版本) 已存在则直接返回原 ID，不重复插入；</li>
 *   <li>保存失败抛 {@link IllegalStateException}，由上层转 FAILED，不能声称"已保存"；</li>
 *   <li>加载失败按"未命中"处理（返回 null），让节点重新执行，不影响主流程。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeekArtifactService {

    private final AgentArtifactService agentArtifactService;
    private final ObjectMapper objectMapper;

    /** 复用的画像产物：artifactId 供报告版本使用。 */
    public record ProfileArtifact(Long artifactId, ResumeProfile profile) {
    }

    /** 复用的候选产物：artifactId 供报告版本使用。 */
    public record CandidatesArtifact(Long artifactId, List<JobCandidates.JobCandidateItem> candidates) {
    }

    /**
     * 保存产物（幂等）。
     *
     * @throws IllegalStateException 保存失败（上层应转 FAILED）
     */
    public Long save(Long userId, Long planId, String type, String version, Object payload, String refs) {
        try {
            AgentArtifact existing = agentArtifactService.findLatest(userId, planId, type, version);
            if (existing != null) {
                return existing.getId(); // 幂等：同版本不重复写
            }
            AgentArtifact artifact = agentArtifactService.save(userId, planId, null,
                    type, version, toJson(payload), refs);
            return artifact.getId();
        } catch (Exception exception) {
            throw new IllegalStateException("结果保存失败（" + type + "）：" + exception.getMessage(), exception);
        }
    }

    /** 加载画像产物（简历/方向版本一致才命中）。 */
    public ProfileArtifact loadProfile(Long userId, Long planId, String version) {
        try {
            AgentArtifact artifact = agentArtifactService.findLatest(
                    userId, planId, AgentArtifactService.TYPE_RESUME_PROFILE, version);
            if (artifact == null || !StringUtils.hasText(artifact.getContentJson())) {
                return null;
            }
            return new ProfileArtifact(artifact.getId(),
                    objectMapper.readValue(artifact.getContentJson(), ResumeProfile.class));
        } catch (Exception exception) {
            log.warn("画像产物解析失败，按未命中处理，error={}", exception.getMessage());
            return null;
        }
    }

    /** 加载候选产物（条件版本一致才命中）。 */
    public CandidatesArtifact loadCandidates(Long userId, Long planId, String constraintVersion) {
        try {
            AgentArtifact artifact = agentArtifactService.findLatest(
                    userId, planId, AgentArtifactService.TYPE_JOB_CANDIDATES, constraintVersion);
            if (artifact == null || !StringUtils.hasText(artifact.getContentJson())) {
                return null;
            }
            List<JobCandidates.JobCandidateItem> items = JobCandidateArtifactCodec.decode(
                    artifact.getContentJson(), objectMapper);
            return items.isEmpty() ? null : new CandidatesArtifact(artifact.getId(), items);
        } catch (Exception exception) {
            log.warn("候选产物解析失败，按未命中处理，error={}", exception.getMessage());
            return null;
        }
    }

    /** 加载匹配报告（报告版本包含上下游产物，版本一致才命中）。 */
    public MatchReport loadReport(Long userId, Long planId, String reportVersion) {
        try {
            AgentArtifact artifact = agentArtifactService.findLatest(
                    userId, planId, AgentArtifactService.TYPE_MATCH_REPORT, reportVersion);
            if (artifact == null || !StringUtils.hasText(artifact.getContentJson())) {
                return null;
            }
            return objectMapper.readValue(artifact.getContentJson(), MatchReport.class);
        } catch (Exception exception) {
            log.warn("匹配报告解析失败，按未命中处理，error={}", exception.getMessage());
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }
}
