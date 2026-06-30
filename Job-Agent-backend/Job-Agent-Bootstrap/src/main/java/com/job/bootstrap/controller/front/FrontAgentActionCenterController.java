package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.AgentActionCenterService;
import com.job.common.dto.agent.AgentActionItemStatusDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentActionItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端 Agent 行动确认中心接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/agent-actions")
public class FrontAgentActionCenterController {

    private final AgentActionCenterService actionCenterService;

    /**
     * 查询当前用户待确认行动项。
     */
    @GetMapping("/pending")
    public Result<List<AgentActionItemVO>> pending(@RequestParam(defaultValue = "50") Integer limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(actionCenterService.listPending(userId, limit), ResultCodeEnum.SUCCESS);
    }

    /**
     * 标记行动项完成。V1 只更新行动项状态，不联动业务表。
     */
    @PostMapping("/{actionId}/done")
    public Result<Void> done(
            @PathVariable Long actionId,
            @RequestBody(required = false) AgentActionItemStatusDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        actionCenterService.markDone(userId, actionId, dto);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 忽略行动项。
     */
    @PostMapping("/{actionId}/ignore")
    public Result<Void> ignore(
            @PathVariable Long actionId,
            @RequestBody(required = false) AgentActionItemStatusDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        actionCenterService.ignore(userId, actionId, dto);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 稍后处理行动项。
     */
    @PostMapping("/{actionId}/snooze")
    public Result<Void> snooze(
            @PathVariable Long actionId,
            @RequestBody AgentActionItemStatusDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        actionCenterService.snooze(userId, actionId, dto);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }
}
