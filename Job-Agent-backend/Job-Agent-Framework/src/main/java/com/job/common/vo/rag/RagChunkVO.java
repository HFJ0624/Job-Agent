package com.job.common.vo.rag;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.rag.RagChunk;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:RAG 切块展示 VO
 * 日期:2026/6/20
 */
@Data
public class RagChunkVO {

    private Long id;

    private Long documentId;

    private Long userId;

    private String documentType;

    private Long businessId;

    private Integer chunkIndex;

    private String title;

    private String content;

    private String contentHash;

    private String source;

    private String metadataJson;

    private String status;

    private String vectorStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastIndexTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static RagChunkVO from(RagChunk chunk) {
        if (chunk == null) {
            return null;
        }

        RagChunkVO vo = new RagChunkVO();
        vo.setId(chunk.getId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setUserId(chunk.getUserId());
        vo.setDocumentType(chunk.getDocumentType());
        vo.setBusinessId(chunk.getBusinessId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setTitle(chunk.getTitle());
        vo.setContent(chunk.getContent());
        vo.setContentHash(chunk.getContentHash());
        vo.setSource(chunk.getSource());
        vo.setMetadataJson(chunk.getMetadataJson());
        vo.setStatus(chunk.getStatus());
        vo.setVectorStatus(chunk.getVectorStatus());
        vo.setLastIndexTime(chunk.getLastIndexTime());
        vo.setCreateTime(chunk.getCreateTime());
        vo.setUpdateTime(chunk.getUpdateTime());
        return vo;
    }
}
