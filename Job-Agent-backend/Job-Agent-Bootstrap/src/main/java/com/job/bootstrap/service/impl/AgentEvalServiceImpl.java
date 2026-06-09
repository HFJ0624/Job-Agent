package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.AgentEvalCaseMapper;
import com.job.bootstrap.mapper.AgentEvalResultMapper;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.service.AgentChatService;
import com.job.bootstrap.service.AgentEvalService;
import com.job.common.entity.agent.AgentEvalCase;
import com.job.common.entity.agent.AgentEvalResult;
import com.job.common.entity.agent.AgentTraceLog;
import com.job.common.vo.agent.AgentChatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 作者: hfj
 * 功能: Agent 自动评测服务实现
 * 设计说明:
 * 1. 这个服务用来验证 Agent 是否稳定调用正确工具。
 * 2. 它不是线上用户功能，而是开发/测试/管理后台使用的质量保障功能。
 * 3. 每次修改 Prompt、Tool 描述、模型配置、匹配算法后，都应该跑一遍评测。
 */
@Service
@RequiredArgsConstructor
public class AgentEvalServiceImpl implements AgentEvalService {

    private final AgentEvalCaseMapper agentEvalCaseMapper;
    private final AgentEvalResultMapper agentEvalResultMapper;
    private final AgentTraceLogMapper agentTraceLogMapper;
    private final AgentChatService agentChatService;
    private final ObjectMapper objectMapper;

    /**
     * 运行单条评测用例。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean runCase(Long caseId) {
        long start = System.currentTimeMillis();

        AgentEvalCase evalCase = agentEvalCaseMapper.selectById(caseId);
        if (evalCase == null || evalCase.getIsDeleted() != 0) {
            throw new IllegalArgumentException("评测用例不存在或已删除");
        }

        /*
         * 记录评测开始时间。
         * 后面查询 trace 时，只查询这个时间之后产生的 trace，避免拿到历史记录。
         */
        Date beginTime = new Date();

        AgentChatVO chatVO = null;
        String failReason = null;
        boolean pass = true;
        List<String> actualTools = new ArrayList<>();

        try {
            /*
             * 调用真实 AgentChatService。
             * 这一步不是 mock，而是真实走完整 Agent 链路：
             * 用户消息 -> 大模型 -> 工具调用 -> 工具 trace -> 最终回答。
             */
            chatVO = agentChatService.chat(
                    evalCase.getUserId(),
                    null,
                    evalCase.getInputMessage()
            );

            /*
             * 根据 conversationId 查询本轮对话产生的工具 trace。
             * 前提：你的工具 trace 必须正确写入 conversationId。
             */
            List<AgentTraceLog> traces = agentTraceLogMapper.selectList(
                    new LambdaQueryWrapper<AgentTraceLog>()
                            .eq(AgentTraceLog::getUserId, evalCase.getUserId())
                            .eq(AgentTraceLog::getConversationId, chatVO.getConversationId())
                            .ge(AgentTraceLog::getCreateTime, beginTime)
                            .isNotNull(AgentTraceLog::getToolName)
            );

            /*
             * 收集实际调用过的工具名。
             */
            for (AgentTraceLog trace : traces) {
                if (StringUtils.hasText(trace.getToolName())) {
                    actualTools.add(trace.getToolName());
                }
            }

            /*
             * 校验 1：是否调用了期望工具。
             */
            if (StringUtils.hasText(evalCase.getExpectedToolName())) {
                boolean toolMatched = actualTools.stream()
                        .anyMatch(tool -> tool.contains(evalCase.getExpectedToolName()));

                if (!toolMatched) {
                    pass = false;
                    failReason = appendReason(
                            failReason,
                            "期望调用工具 " + evalCase.getExpectedToolName() + "，实际调用 " + actualTools
                    );
                }
            }

            /*
             * 校验 2：最终回答是否包含期望关键词。
             */
            if (StringUtils.hasText(evalCase.getExpectedAnswerKeywords())) {
                String[] keywords = evalCase.getExpectedAnswerKeywords().split(",");

                for (String keyword : keywords) {
                    String trimKeyword = keyword.trim();
                    if (StringUtils.hasText(trimKeyword)
                            && !chatVO.getAnswer().contains(trimKeyword)) {
                        pass = false;
                        failReason = appendReason(
                                failReason,
                                "回答缺少关键词：" + trimKeyword
                        );
                    }
                }
            }

        } catch (Exception e) {
            pass = false;
            failReason = appendReason(failReason, "执行异常：" + e.getMessage());
        }

        /*
         * 保存评测结果。
         * 即使失败也要保存，方便后续分析失败原因。
         */
        AgentEvalResult result = new AgentEvalResult();
        result.setCaseId(evalCase.getId());
        result.setUserId(evalCase.getUserId());
        result.setConversationId(chatVO == null ? null : chatVO.getConversationId());
        result.setInputMessage(evalCase.getInputMessage());
        result.setActualAnswer(chatVO == null ? null : chatVO.getAnswer());
        result.setActualTools(toJson(actualTools));
        result.setPassStatus(pass ? 1 : 0);
        result.setFailReason(failReason);
        result.setCostTime(System.currentTimeMillis() - start);

        agentEvalResultMapper.insert(result);

        return pass;
    }

    /**
     * 运行所有启用的测试用例。
     */
    @Override
    public Integer runAllEnabledCases() {
        List<AgentEvalCase> cases = agentEvalCaseMapper.selectList(
                new LambdaQueryWrapper<AgentEvalCase>()
                        .eq(AgentEvalCase::getEnableStatus, 1)
                        .eq(AgentEvalCase::getIsDeleted, 0)
        );

        int passCount = 0;

        for (AgentEvalCase evalCase : cases) {
            Boolean pass = runCase(evalCase.getId());
            if (Boolean.TRUE.equals(pass)) {
                passCount++;
            }
        }

        return passCount;
    }

    /**
     * 拼接失败原因。
     */
    private String appendReason(String oldReason, String newReason) {
        if (!StringUtils.hasText(oldReason)) {
            return newReason;
        }
        return oldReason + "；" + newReason;
    }

    /**
     * 对象转 JSON。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
