package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.AgentInboxService;
import com.job.common.entity.base.Result;
import com.job.common.vo.agent.AgentInboxVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 Agent Inbox 接口。
 */
@Tag(name = "用户端 Agent Inbox 接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/agent-inbox")
public class FrontAgentInboxController {

    private final AgentInboxService agentInboxService;

    /**
     * 查询今日 Agent 待办。
     */
    @Operation(summary = "查询今日 Agent 待办")
    @GetMapping("/today")
    public Result<AgentInboxVO> today() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(agentInboxService.getTodayInbox(userId), 200, "查询成功");
    }
}
