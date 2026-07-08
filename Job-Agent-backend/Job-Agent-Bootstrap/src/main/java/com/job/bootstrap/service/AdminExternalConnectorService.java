package com.job.bootstrap.service;

import com.job.common.agent.tool.AgentToolSchema;

import java.util.List;
import java.util.Map;

/**
 * 后台外部连接器管理服务。
 *
 * <p>核心职责：为管理员提供系统已注册外部连接器工具（如邮件发送、日历同步、第三方 API 等）的查询与预览调试能力。第一版仅管理代码中已注册的工具，不读写数据库配置。</p>
 *
 * <p>所属业务模块：Agent 工具生态 / 外部连接器管理</p>
 *
 * <p>主要调用链：Admin Controller → AdminExternalConnectorService → AgentToolRegistry / 外部连接器执行器</p>
 */
public interface AdminExternalConnectorService {

    /**
     * 查询所有已注册的外部连接器工具 Schema 列表。
     *
     * @return 连接器工具 Schema 列表，包含工具名称、描述、参数定义、返回结构等元信息
     */
    List<AgentToolSchema> listConnectorTools();

    /**
     * 查询单个外部连接器工具 Schema。
     *
     * @param toolName 工具唯一名称标识
     * @return 工具 Schema 详情
     */
    AgentToolSchema getConnectorTool(String toolName);

    /**
     * 预览调用外部连接器工具，用于管理员调试验证。
     *
     * @param toolName 工具唯一名称标识
     * @param params   工具调用参数（键值对形式，需符合工具 Schema 定义）
     * @return 工具调用返回的 JSON 字符串
     */
    String preview(String toolName, Map<String, Object> params);
}
