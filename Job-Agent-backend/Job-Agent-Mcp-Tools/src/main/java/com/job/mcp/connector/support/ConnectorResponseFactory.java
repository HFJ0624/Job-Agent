package com.job.mcp.connector.support;

import cn.hutool.json.JSONUtil;
import com.job.mcp.connector.model.ConnectorSideEffectType;
import com.job.mcp.connector.model.ConnectorToolResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部连接器响应工厂。
 * 这里集中生成统一 JSON，避免每个工具重复拼接字段，也方便第二版统一接入真实适配器。
 */
public final class ConnectorResponseFactory {

    private ConnectorResponseFactory() {
    }

    /**
     * 构建第一版预览响应。
     *
     * @param toolName                 工具唯一名称，必须是“类名.方法名”
     * @param connectorType            连接器类型
     * @param providerCode             平台或渠道编码
     * @param sideEffectType           副作用类型
     * @param requiresUserConfirmation 是否需要用户确认
     * @param message                  返回给 Agent 的说明
     * @param request                  结构化请求参数
     * @param data                     预览数据
     * @return 统一 JSON 字符串
     */
    public static String preview(
            String toolName,
            String connectorType,
            String providerCode,
            ConnectorSideEffectType sideEffectType,
            boolean requiresUserConfirmation,
            String message,
            Map<String, Object> request,
            Map<String, Object> data
    ) {
        /*
         * 第一版所有外部连接器只做“参数结构化 + 执行预览”。
         * 这样 Agent 可以先规划和选择工具，但不会误发邮件、误同步岗位、误创建日程。
         */
        ConnectorToolResponse response = ConnectorToolResponse.builder()
                .toolName(toolName)
                .connectorType(connectorType)
                .providerCode(normalize(providerCode))
                .status("PREVIEW")
                .sideEffectType(sideEffectType)
                .requiresUserConfirmation(requiresUserConfirmation)
                .requiresRealAdapter(true)
                .message(message)
                .request(request == null ? Map.of() : request)
                .data(data == null ? Map.of() : data)
                .build();
        return JSONUtil.toJsonStr(response);
    }

    /**
     * 用有序 Map 保存参数，保证测试、日志和排查时字段顺序稳定。
     */
    public static Map<String, Object> orderedRequest(Object... keyValues) {
        Map<String, Object> request = new LinkedHashMap<>();
        if (keyValues == null) {
            return request;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            request.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return request;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }
}
