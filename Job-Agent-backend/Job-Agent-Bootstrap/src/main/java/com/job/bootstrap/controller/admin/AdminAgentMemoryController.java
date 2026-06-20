package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.dto.agent.AgentMemoryQueryDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentMemoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者:hfj
 * 功能:后台 Agent 长期记忆查询接口
 * 日期:2026/6/20
 *
 * 说明:
 * 1. 该接口面向后台管理人员，用来查看用户被 Agent 沉淀了哪些长期记忆。
 * 2. 第一版只提供查询能力，不提供人工新增、修改、删除，避免记忆治理规则还没稳定时误改数据。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/memories")
public class AdminAgentMemoryController {

    private final AgentMemoryService agentMemoryService;

    /**
     * 分页查询长期记忆。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<IPage<AgentMemoryVO>> pageMemories(AgentMemoryQueryDTO query) {
        return Result.build(agentMemoryService.pageMemories(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 按用户和关键词检索长期记忆。
     *
     * @param userId 用户 ID
     * @param query 检索词
     * @param limit 召回数量
     * @return 相关记忆列表
     */
    @GetMapping("/search")
    public Result<List<AgentMemoryVO>> search(
            @RequestParam Long userId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer limit
    ) {
        return Result.build(agentMemoryService.searchMemories(userId, query, limit), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询长期记忆详情。
     *
     * @param id 记忆 ID
     * @return 记忆详情
     */
    @GetMapping("/{id}")
    public Result<AgentMemoryVO> detail(@PathVariable Long id) {
        return Result.build(agentMemoryService.getDetail(id), ResultCodeEnum.SUCCESS);
    }
}
