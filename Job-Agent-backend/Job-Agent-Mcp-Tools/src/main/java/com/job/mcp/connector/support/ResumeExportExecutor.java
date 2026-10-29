package com.job.mcp.connector.support;

/**
 * 功能: 简历导出执行器接口（任务 J3）。
 *
 * 设计说明:
 * 1. 模块边界：连接器工具在 Mcp-Tools 模块，而简历加载、对象存储能力在 Bootstrap 模块
 *    （Mcp-Tools 不依赖 Bootstrap）。这里在 Mcp-Tools 定义接口，Bootstrap 出实现，
 *    工具只依赖接口——真实执行能力可替换、可单测（mock 接口）；
 * 2. 安全边界：实现方必须做归属校验（userId + resumeId），越权/不存在抛 BizException，
 *    由工具转成结构化 ERROR 响应，不泄露简历是否存在；
 * 3. 幂等：实现方对同一 userId + resumeId + format 使用稳定对象键，重复导出覆盖同一对象。
 */
public interface ResumeExportExecutor {

    /**
     * 真实执行简历导出。
     *
     * @param userId   当前用户（来自 Agent 运行时上下文，不允许模型传）
     * @param resumeId 简历 ID
     * @param format   导出格式（大写，调用方已归一化）
     * @return 导出结果（可核对的文件信息）
     * @throws com.job.exception.BizException 归属校验失败或存储失败
     */
    ExportResult export(Long userId, Long resumeId, String format);

    /**
     * 导出结果。
     *
     * @param resumeName 简历名称
     * @param filename   生成的文件名
     * @param fileKey    对象存储键（回查凭据）
     * @param fileUrl    访问 URL
     * @param fileSize   文件大小（字节）
     * @param contentType MIME 类型
     */
    record ExportResult(
            String resumeName,
            String filename,
            String fileKey,
            String fileUrl,
            Long fileSize,
            String contentType
    ) {
    }
}
