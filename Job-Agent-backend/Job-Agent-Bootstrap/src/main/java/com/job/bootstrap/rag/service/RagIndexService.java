package com.job.bootstrap.rag.service;

import com.job.common.vo.rag.RagIndexResultVO;

/**
 * 作者:hfj
 * 功能:RAG 知识库索引服务接口
 * 日期:2026/6/14
 */
public interface RagIndexService {

    /**
     * 重建当前用户可用的全部知识。
     * 包括公共岗位/公司知识，以及当前用户的简历/沟通知识。
     *
     * @param userId 当前登录用户 ID
     * @return 索引结果
     */
    RagIndexResultVO rebuildAllKnowledge(Long userId);

    /**
     * 只重建公共知识。
     *
     * @return 索引结果
     */
    RagIndexResultVO rebuildPublicKnowledge();

    /**
     * 重建所有用户可用的全部知识。
     * 该方法面向后台管理员，用于统一刷新公共知识和所有用户私有知识。
     *
     * @return 索引结果
     */
    RagIndexResultVO rebuildAllUserKnowledge();

    /**
     * 只重建当前用户私有知识。
     *
     * @param userId 当前登录用户 ID
     * @return 索引结果
     */
    RagIndexResultVO rebuildUserKnowledge(Long userId);
}
