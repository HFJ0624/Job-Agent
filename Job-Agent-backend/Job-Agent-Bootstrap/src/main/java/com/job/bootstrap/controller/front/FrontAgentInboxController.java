package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.AgentInboxService;
import com.job.common.dto.agent.AgentInboxActionDTO;
import com.job.common.entity.base.Result;
import com.job.common.vo.agent.AgentInboxVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 标记待办完成。
     */
    @Operation(summary = "标记 Agent 待办完成")
    @PostMapping("/items/{itemKey}/done")
    public Result<Void> markDone(
            @PathVariable String itemKey,
            @RequestBody(required = false) AgentInboxActionDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        agentInboxService.markDone(userId, itemKey, dto);
        return Result.build(null, 200, "已标记完成");
    }

    /**
     * 忽略待办。
     */
    @Operation(summary = "忽略 Agent 待办")
    @PostMapping("/items/{itemKey}/ignore")
    public Result<Void> ignore(
            @PathVariable String itemKey,
            @RequestBody(required = false) AgentInboxActionDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        agentInboxService.ignore(userId, itemKey, dto);
        return Result.build(null, 200, "已忽略");
    }

    /**
     * 稍后提醒待办。
     */
    @Operation(summary = "稍后提醒 Agent 待办")
    @PostMapping("/items/{itemKey}/snooze")
    public Result<Void> snooze(
            @PathVariable String itemKey,
            @RequestBody AgentInboxActionDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        agentInboxService.snooze(userId, itemKey, dto);
        return Result.build(null, 200, "已设置稍后提醒");
    }
}
