package com.job.bootstrap.agent.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.job.bootstrap.mapper.AgentArtifactMapper;
import com.job.common.entity.agent.AgentArtifact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 功能: Agent 产物服务（第二阶段）。
 *
 * 设计说明:
 * 1. 只存结构化产物，写操作是**短事务**（REQUIRES_NEW）——Agent 执行在事务外，产物落库独立提交；
 * 2. 读取按 userId + planId + type + inputVersion 定位：版本不一致即视为失效，
 *    避免条件变化后复用旧画像/旧候选；
 * 3. 完整 JSON 落库，摘要不参与恢复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentArtifactService {

    public static final String TYPE_RESUME_PROFILE = "RESUME_PROFILE";
    public static final String TYPE_JOB_CANDIDATES = "JOB_CANDIDATES";
    public static final String TYPE_MATCH_REPORT = "MATCH_REPORT";

    private static final int NOT_DELETED = 0;
    private static final String SCHEMA_V1 = "v1";

    private final AgentArtifactMapper agentArtifactMapper;

    /**
     * 保存产物（短事务）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AgentArtifact save(Long userId, Long planId, Long runId, String type,
                              String inputVersion, String contentJson, String sourceRefs) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setUserId(userId);
        artifact.setPlanId(planId);
        artifact.setRunId(runId);
        artifact.setType(type);
        artifact.setSchemaVersion(SCHEMA_V1);
        artifact.setInputVersion(inputVersion);
        artifact.setContentJson(contentJson);
        artifact.setSourceRefs(sourceRefs);
        artifact.setIsDeleted(NOT_DELETED);
        artifact.setCreateTime(new Date());
        artifact.setUpdateTime(new Date());
        agentArtifactMapper.insert(artifact);
        return artifact;
    }

    /**
     * 按条件版本取最新产物（不存在返回 null）。
     */
    public AgentArtifact findLatest(Long userId, Long planId, String type, String inputVersion) {
        if (userId == null || planId == null || !StringUtils.hasText(type)) {
            return null;
        }
        QueryWrapper<AgentArtifact> wrapper = new QueryWrapper<AgentArtifact>()
                .eq("user_id", userId)
                .eq("plan_id", planId)
                .eq("type", type)
                .eq("is_deleted", NOT_DELETED);
        if (StringUtils.hasText(inputVersion)) {
            wrapper.eq("input_version", inputVersion);
        }
        return agentArtifactMapper.selectOne(wrapper.orderByDesc("id").last("limit 1"));
    }

    /**
     * 按 ID 加载并校验归属。
     */
    public AgentArtifact loadOwned(Long userId, Long artifactId) {
        if (artifactId == null) {
            return null;
        }
        AgentArtifact artifact = agentArtifactMapper.selectById(artifactId);
        if (artifact == null || !userId.equals(artifact.getUserId())) {
            return null;
        }
        return artifact;
    }

    /**
     * 列出某计划下某类型的全部产物（版本变化审计/调试用）。
     */
    public List<AgentArtifact> listByPlan(Long userId, Long planId, String type) {
        QueryWrapper<AgentArtifact> wrapper = new QueryWrapper<AgentArtifact>()
                .eq("user_id", userId)
                .eq("plan_id", planId)
                .eq("is_deleted", NOT_DELETED);
        if (StringUtils.hasText(type)) {
            wrapper.eq("type", type);
        }
        return agentArtifactMapper.selectList(wrapper.orderByAsc("id"));
    }
}
