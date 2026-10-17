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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 作者:hfj
 * 功能:前台 AI 求职助手接口
 * 日期: 2026/6/8 15:22
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/agent")
public class AgentController {

    /**
     * SSE 流式超时时间（毫秒）。
     * 需要覆盖“规划 + 执行 + 流式总结”的完整时长，取 5 分钟。
     */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final AgentChatService agentChatService;

    private final AgentHistoryService agentHistoryService;

    /**
     * SSE 异步执行器：流式期间不占用 Tomcat 请求线程。
     */
    private final @Qualifier("agentSseExecutor") Executor agentSseExecutor;

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

        /*
         * retry 为 true 表示用户要恢复上次失败的计划，需同时传回 planId 和 confirmedToolNames。
         */
        AgentChatVO result = agentChatService.chat(
                userId,
                request.getConversationId(),
                request.getPlanId(),
                request.getMessage(),
                request.getConfirmedToolNames(),
                request.getRetry()
        );

        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * AI 对话接口（SSE 流式，任务 G5）。
     *
     * 事件协议:
     * - token: 增量文本（模型流式输出的 delta；降级时只有一个整段 token）
     * - done:  完整 AgentChatVO（流式结束，前端以此为准校正渲染）
     * - error: 错误信息
     *
     * 说明:
     * 1. 主链路（护栏/规划/执行/记忆/落库）与阻塞接口完全一致，仅总结生成流式推送；
     * 2. 整个链路在独立线程执行，不占用 Tomcat 请求线程；
     * 3. 客户端断开/超时直接结束 emitter，服务端链路照常完成并落库。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody AgentChatRequestDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        agentSseExecutor.execute(() -> {
            try {
                AgentChatVO result = agentChatService.streamChat(
                        userId,
                        request.getConversationId(),
                        request.getPlanId(),
                        request.getMessage(),
                        request.getConfirmedToolNames(),
                        request.getRetry(),
                        token -> sendToken(emitter, token)
                );
                sendEvent(emitter, "done", result);
                emitter.complete();
            } catch (Exception exception) {
                log.warn("SSE 流式对话失败，userId={}, error={}", userId, exception.getMessage());
                sendEvent(emitter, "error", exception.getMessage());
                emitter.complete();
            }
        });

        emitter.onTimeout(() -> emitter.complete());
        emitter.onError(error -> emitter.complete());
        return emitter;
    }

    // 推送增量 token：客户端断开时 send 抛异常，静默吞掉（由 onError/onTimeout 收尾）
    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (Exception exception) {
            log.debug("SSE token 推送失败（客户端可能已断开），error={}", exception.getMessage());
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception exception) {
            log.debug("SSE 事件推送失败，name={}, error={}", name, exception.getMessage());
        }
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
