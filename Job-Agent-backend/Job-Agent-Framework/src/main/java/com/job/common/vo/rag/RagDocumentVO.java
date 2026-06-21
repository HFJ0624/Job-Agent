package com.job.common.vo.rag;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.rag.RagDocument;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:RAG 文档展示 VO
 * 日期:2026/6/20
 */
@Data
public class RagDocumentVO {

    private Long id;

    private Long userId;

    private String documentType;

    private Long businessId;

    private String title;

    private String source;

    private String permissionScope;

    private String contentHash;

    private Integer chunkCount;

    private String status;

    private String indexStatus;

    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastIndexTime;

    private String metadataJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static RagDocumentVO from(RagDocument document) {
        if (document == null) {
            return null;
        }

        RagDocumentVO vo = new RagDocumentVO();
        vo.setId(document.getId());
        vo.setUserId(document.getUserId());
        vo.setDocumentType(document.getDocumentType());
        vo.setBusinessId(document.getBusinessId());
        vo.setTitle(document.getTitle());
        vo.setSource(document.getSource());
        vo.setPermissionScope(document.getPermissionScope());
        vo.setContentHash(document.getContentHash());
        vo.setChunkCount(document.getChunkCount());
        vo.setStatus(document.getStatus());
        vo.setIndexStatus(document.getIndexStatus());
        vo.setErrorMsg(document.getErrorMsg());
        vo.setLastIndexTime(document.getLastIndexTime());
        vo.setMetadataJson(document.getMetadataJson());
        vo.setCreateTime(document.getCreateTime());
        vo.setUpdateTime(document.getUpdateTime());
        return vo;
    }
}
