package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AdminAgentActionItemService;
import com.job.common.dto.agent.AgentActionItemQueryDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentActionItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Agent 行动项管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/actions")
public class AdminAgentActionItemController {

    private final AdminAgentActionItemService actionItemService;

    /**
     * 分页查询 Agent 行动项。
     */
    @GetMapping("/page")
    public Result<IPage<AgentActionItemVO>> page(AgentActionItemQueryDTO query) {
        return Result.build(actionItemService.pageActions(query), ResultCodeEnum.SUCCESS);
    }
}
