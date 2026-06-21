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

    /**
     * 增量索引单个业务文档。
     *
     * @param userId 用户 ID；公共文档可传 0
     * @param documentType 文档类型，例如 JOB、COMPANY、RESUME
     * @param businessId 来源业务 ID
     * @return 索引结果
     */
    RagIndexResultVO indexDocument(Long userId, String documentType, Long businessId);

    /**
     * 同步删除单个业务文档的 RAG 索引。
     *
     * @param userId 用户 ID；公共文档可传 0
     * @param documentType 文档类型
     * @param businessId 来源业务 ID
     * @return 删除同步结果
     */
    RagIndexResultVO deleteDocument(Long userId, String documentType, Long businessId);
}
