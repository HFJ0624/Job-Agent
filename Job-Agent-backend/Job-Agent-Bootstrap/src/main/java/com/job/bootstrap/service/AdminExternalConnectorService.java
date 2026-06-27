package com.job.bootstrap.service;

import com.job.common.agent.tool.AgentToolSchema;

import java.util.List;
import java.util.Map;

/**
 * 后台外部连接器管理服务。
 * 第一版只管理已经注册到代码里的连接器工具，不读写数据库配置。
 */
public interface AdminExternalConnectorService {

    /**
     * 查询所有外部连接器工具 Schema。
     *
     * @return 连接器工具列表
     */
    List<AgentToolSchema> listConnectorTools();

    /**
     * 查询单个外部连接器工具 Schema。
     *
     * @param toolName 工具唯一名称
     * @return 工具 Schema
     */
    AgentToolSchema getConnectorTool(String toolName);

    /**
     * 预览调用外部连接器工具。
     *
     * @param toolName 工具唯一名称
     * @param params 工具参数
     * @return 工具返回 JSON
     */
    String preview(String toolName, Map<String, Object> params);
}
