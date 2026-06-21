package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.rag.service.RagIndexService;
import com.job.bootstrap.rag.service.RagKnowledgeService;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.common.dto.rag.RagChunkQueryDTO;
import com.job.common.dto.rag.RagDocumentQueryDTO;
import com.job.common.dto.rag.RagRetrievalEvalDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.rag.RagChunkVO;
import com.job.common.vo.rag.RagDocumentVO;
import com.job.common.vo.rag.RagIndexResultVO;
import com.job.common.vo.rag.RagRetrievalEvalVO;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.common.vo.rag.RagStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final RagKnowledgeService ragKnowledgeService;

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
     * 增量索引单个业务文档。
     *
     * @param userId 用户 ID；JOB/COMPANY 可传 0
     * @param documentType 文档类型
     * @param businessId 业务 ID
     * @return 索引结果
     */
    @PostMapping("/index/document")
    public Result<RagIndexResultVO> indexDocument(
            @RequestParam(defaultValue = "0") Long userId,
            @RequestParam String documentType,
            @RequestParam Long businessId
    ) {
        RagIndexResultVO result = ragIndexService.indexDocument(userId, documentType, businessId);
        return Result.build(result, ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除同步单个业务文档。
     *
     * @param userId 用户 ID；JOB/COMPANY 可传 0
     * @param documentType 文档类型
     * @param businessId 业务 ID
     * @return 同步结果
     */
    @DeleteMapping("/index/document")
    public Result<RagIndexResultVO> deleteDocument(
            @RequestParam(defaultValue = "0") Long userId,
            @RequestParam String documentType,
            @RequestParam Long businessId
    ) {
        RagIndexResultVO result = ragIndexService.deleteDocument(userId, documentType, businessId);
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

    /**
     * 后台评测单条 RAG 查询的召回质量。
     *
     * @param query 评测参数
     * @return 命中、命中排名、MRR 和召回明细
     */
    @GetMapping("/search/evaluate")
    public Result<RagRetrievalEvalVO> evaluate(RagRetrievalEvalDTO query) {
        return Result.build(ragRetrievalService.evaluate(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台分页查看 RAG 文档。
     *
     * @param query 查询条件
     * @return 文档分页
     */
    @GetMapping("/documents/page")
    public Result<IPage<RagDocumentVO>> pageDocuments(RagDocumentQueryDTO query) {
        return Result.build(ragKnowledgeService.pageDocuments(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台分页查看 RAG 切块。
     *
     * @param query 查询条件
     * @return 切块分页
     */
    @GetMapping("/chunks/page")
    public Result<IPage<RagChunkVO>> pageChunks(RagChunkQueryDTO query) {
        return Result.build(ragKnowledgeService.pageChunks(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台查看切块详情。
     *
     * @param id 切块 ID
     * @return 切块详情
     */
    @GetMapping("/chunks/{id}")
    public Result<RagChunkVO> chunkDetail(@PathVariable Long id) {
        return Result.build(ragKnowledgeService.getChunkDetail(id), ResultCodeEnum.SUCCESS);
    }
}
