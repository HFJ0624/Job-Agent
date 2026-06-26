package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AgentMemoryContextService;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.dto.agent.AgentMemoryQueryDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentMemoryHistoryVO;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.common.vo.agent.AgentUserMemoryProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final AgentMemoryContextService agentMemoryContextService;

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

    /**
     * 查询长期记忆版本历史。
     *
     * @param id 记忆 ID
     * @return 历史版本列表
     */
    @GetMapping("/{id}/history")
    public Result<List<AgentMemoryHistoryVO>> history(@PathVariable Long id) {
        return Result.build(agentMemoryService.listHistory(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台人工更新长期记忆状态。
     *
     * 方法步骤:
     * 1. 先更新单条记忆状态，支持 ACTIVE、ARCHIVED、INVALID。
     * 2. 再按这条记忆的 userId 重建用户画像。
     * 3. 返回更新后的记忆，方便前端刷新当前行。
     *
     * 为什么状态更新后要重建画像:
     * - Agent 实际注入 Prompt 时优先使用“压缩画像 + 少量相关记忆”。
     * - 如果只禁用原始记忆，不刷新画像，错误事实仍可能残留在画像摘要里。
     *
     * @param id 记忆 ID
     * @param status 目标状态
     * @return 更新后的记忆
     */
    @PutMapping("/{id}/status")
    public Result<AgentMemoryVO> updateStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        AgentMemoryVO memory = agentMemoryService.updateStatus(id, status);
        if (memory != null && memory.getUserId() != null) {
            agentMemoryContextService.rebuildProfile(memory.getUserId());
        }
        return Result.build(memory, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询用户长期记忆画像。
     *
     * @param userId 用户 ID
     * @return 压缩后的用户画像
     */
    @GetMapping("/profiles/{userId}")
    public Result<AgentUserMemoryProfileVO> profile(@PathVariable Long userId) {
        return Result.build(agentMemoryContextService.getProfile(userId), ResultCodeEnum.SUCCESS);
    }

    /**
     * 手动重建用户长期记忆画像。
     *
     * 说明:
     * 1. 当后台人工修正记忆、禁用错误记忆后，可以调用该接口重建画像。
     * 2. 重建只读取 ACTIVE 的长期记忆，不会把已禁用或已删除记忆重新注入 Prompt。
     *
     * @param userId 用户 ID
     * @return 重建后的画像
     */
    @PostMapping("/profiles/{userId}/rebuild")
    public Result<AgentUserMemoryProfileVO> rebuildProfile(@PathVariable Long userId) {
        return Result.build(agentMemoryContextService.rebuildProfile(userId), ResultCodeEnum.SUCCESS);
    }
}
