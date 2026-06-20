package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.AgentChatService;
import com.job.bootstrap.service.AgentHistoryService;
import com.job.common.dto.agent.AgentChatRequestDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentChatVO;
import com.job.common.vo.agent.AgentConversationVO;
import com.job.common.vo.agent.AgentMessageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 作者:hfj
 * 功能:前台 AI 求职助手接口
 * 日期: 2026/6/8 15:22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/agent")
public class AgentController {

    private final AgentChatService agentChatService;

    private final AgentHistoryService agentHistoryService;

    /**
     * AI 对话接口。
     *
     * @param request 用户消息
     * @return AI 回复
     */
    @PostMapping("/chat")
    public Result<AgentChatVO> chat(@Valid @RequestBody AgentChatRequestDTO request) {
        /*
         * 用户ID必须从登录态获取，避免前端伪造。
         */
        Long userId = StpUtil.getLoginIdAsLong();

        AgentChatVO result = agentChatService.chat(
                userId,
                request.getConversationId(),
                request.getMessage(),
                request.getConfirmedToolNames()
        );

        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询当前用户的 AI 会话列表。
     *
     * @return 会话列表
     */
    @GetMapping("/conversations")
    public Result<List<AgentConversationVO>> listConversations() {
        Long userId = StpUtil.getLoginIdAsLong();

        List<AgentConversationVO> list = agentHistoryService.listConversations(userId);

        return Result.build(list, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询某个会话下的历史消息。
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<AgentMessageVO>> listMessages(@PathVariable Long conversationId) {
        Long userId = StpUtil.getLoginIdAsLong();

        List<AgentMessageVO> list = agentHistoryService.listMessages(userId, conversationId);

        return Result.build(list, ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除某个会话。
     *
     * @param conversationId 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable Long conversationId) {
        Long userId = StpUtil.getLoginIdAsLong();

        agentHistoryService.deleteConversation(userId, conversationId);

        return Result.build(null, ResultCodeEnum.SUCCESS);
    }
}
