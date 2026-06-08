package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.AgentChatService;
import com.job.common.dto.agent.AgentChatRequestDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentChatVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                request.getMessage()
        );

        return Result.build(result, ResultCodeEnum.SUCCESS);
    }
}
