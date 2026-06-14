package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.rag.RagSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者:hfj
 * 功能:前台 RAG 知识库接口
 * 日期:2026/6/14
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/rag")
public class FrontRagController {

    private final RagRetrievalService ragRetrievalService;

    /**
     * 直接检索 RAG 知识库。
     *
     * @param query 用户问题或检索词
     * @param limit 召回条数
     * @return 召回结果
     */
    @GetMapping("/search")
    public Result<List<RagSearchResultVO>> search(
            @RequestParam String query,
            @RequestParam(required = false) Integer limit
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<RagSearchResultVO> result = ragRetrievalService.search(userId, query, limit);
        return Result.build(result, ResultCodeEnum.SUCCESS);
    }
}
