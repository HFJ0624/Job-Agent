package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 功能: 模拟面试音视频记录实体。
 *
 * 说明:
 * 1. 第一版只保存每道题的回答音频，不保存整段视频，避免文件体积和隐私风险过早放大。
 * 2. ASR 识别结果也落在这张表，方便后台按会话、题目、回答追溯原始音频和转写文本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_media_record")
public class MockInterviewMediaRecord extends BaseEntity {

    /**
     * 模拟面试会话ID。
     */
    private Long sessionId;

    /**
     * 当前音频对应的题目ID。
     */
    private Long questionId;

    /**
     * 当前音频最终生成的回答ID，ASR 成功并完成评分后回填。
     */
    private Long answerId;

    /**
     * 用户ID，用于权限过滤和后台审计。
     */
    private Long userId;

    /**
     * 媒体类型: AUDIO / VIDEO。
     */
    private String mediaType;

    /**
     * 文件访问地址。
     */
    private String fileUrl;

    /**
     * MinIO 对象名。
     */
    private String objectName;

    /**
     * 原始文件名。
     */
    private String fileName;

    /**
     * 文件大小，单位字节。
     */
    private Long fileSize;

    /**
     * 浏览器上报或后续分析得到的时长秒数。
     */
    private Integer durationSeconds;

    /**
     * ASR 识别文本。
     */
    private String asrText;

    /**
     * ASR 服务商，例如 VOLCENGINE。
     */
    private String asrProvider;

    /**
     * ASR 状态: PENDING / SUCCESS / FAILED。
     */
    private String asrStatus;

    /**
     * ASR 失败原因。
     */
    private String asrError;
}
