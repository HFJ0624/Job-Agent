package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentTraceLog;
import lombok.Data;

import java.util.Date;


/**
 * 作者:hfj
 * 功能:Agent Trace 日志展示 VO
 * 说明:
 * 1. 后台页面展示日志列表和详情时使用。
 * 2. 不直接返回 Entity，方便后续隐藏敏感字段或做字段格式化。
 * 日期: 2026/6/8 20:04
 */
@Data
public class AgentTraceLogVO {

    private Long id;

    /**
     * 链路ID。
     */
    private String traceId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 意图编码。
     */
    private String intentCode;

    /**
     * 工具名称。
     */
    private String toolName;

    /**
     * 输入数据 JSON。
     */
    private String inputData;

    /**
     * 输出数据 JSON。
     */
    private String outputData;

    /**
     * 调用状态。
     */
    private String status;

    /**
     * 错误信息。
     */
    private String errorMsg;

    /**
     * 耗时，单位毫秒。
     */
    private Long costTime;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * Entity 转 VO。
     *
     * @param log 日志实体
     * @return 日志 VO
     */
    public static AgentTraceLogVO from(AgentTraceLog log) {
        if (log == null) {
            return null;
        }

        AgentTraceLogVO vo = new AgentTraceLogVO();
        vo.setId(log.getId());
        vo.setTraceId(log.getTraceId());
        vo.setUserId(log.getUserId());
        vo.setConversationId(log.getConversationId());
        vo.setIntentCode(log.getIntentCode());
        vo.setToolName(log.getToolName());
        vo.setInputData(log.getInputData());
        vo.setOutputData(log.getOutputData());
        vo.setStatus(log.getStatus());
        vo.setErrorMsg(log.getErrorMsg());
        vo.setCostTime(log.getCostTime());
        vo.setCreateTime(log.getCreateTime());
        vo.setUpdateTime(log.getUpdateTime());
        return vo;
    }
}
