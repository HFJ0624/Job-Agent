package com.job.bootstrap.controller.admin;

import com.job.bootstrap.rag.service.RagIndexService;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.rag.RagIndexResultVO;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.common.vo.rag.RagStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者:hfj
 * 功能:后台 RAG 知识库管理接口
 * 日期:2026/6/14
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/rag")
public class AdminRagController {

    private final RagIndexService ragIndexService;
    private final RagRetrievalService ragRetrievalService;
    private final RagVectorStoreService ragVectorStoreService;

    /**
     * 查询 RAG 知识库统计信息。
     *
     * @return RAG 统计信息
     */
    @GetMapping("/stats")
    public Result<RagStatsVO> stats() {
        /*
         * 后台页面需要看到:
         * 1. 当前向量表和维度。
         * 2. 公共知识、用户私有知识的分片数量。
         * 3. 不同文档类型的索引覆盖情况。
         */
        RagStatsVO stats = ragVectorStoreService.getStats();
        return Result.build(stats, ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台一键重建全部 RAG 知识。
     *
     * @return 索引统计结果
     */
    @PostMapping("/index/all")
    public Result<RagIndexResultVO> rebuildAll() {
        RagIndexResultVO result = ragIndexService.rebuildAllUserKnowledge();
        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台只重建公共知识。
     *
     * @return 索引统计结果
     */
    @PostMapping("/index/public")
    public Result<RagIndexResultVO> rebuildPublic() {
        RagIndexResultVO result = ragIndexService.rebuildPublicKnowledge();
        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台重建指定用户的私有知识。
     *
     * @param userId 用户 ID
     * @return 索引统计结果
     */
    @PostMapping("/index/users/{userId}")
    public Result<RagIndexResultVO> rebuildUser(@PathVariable Long userId) {
        RagIndexResultVO result = ragIndexService.rebuildUserKnowledge(userId);
        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台预览 RAG 检索效果。
     *
     * @param userId 用户 ID，0 表示只预览公共知识
     * @param query 检索问题
     * @param limit 召回条数
     * @return 召回结果
     */
    @GetMapping("/search")
    public Result<List<RagSearchResultVO>> search(
            @RequestParam(defaultValue = "0") Long userId,
            @RequestParam String query,
            @RequestParam(required = false) Integer limit
    ) {
        List<RagSearchResultVO> result = ragRetrievalService.search(userId, query, limit);
        return Result.build(result, ResultCodeEnum.SUCCESS);
    }
}
