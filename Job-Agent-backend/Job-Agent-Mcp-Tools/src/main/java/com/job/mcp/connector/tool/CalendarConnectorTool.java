package com.job.mcp.connector.tool;

import com.job.mcp.connector.model.ConnectorSideEffectType;
import com.job.mcp.connector.support.ConnectorResponseFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 日历连接器工具。
 * 用于把面试邀约、笔试时间等求职事件写入外部日历。
 */
@Component
public class CalendarConnectorTool {

    private static final String CREATE_TOOL_NAME = "CalendarConnectorTool.createInterviewEvent";

    @Tool("创建面试日历事件。第一版只返回创建预览，不真正写入外部日历。")
    public String createInterviewEvent(
            @P("日历渠道编码，例如 google-calendar、outlook-calendar") String providerCode,
            @P("事件标题，例如 字节后端一面") String title,
            @P("开始时间，例如 2026-07-01 10:00") String startTime,
            @P("地点或会议链接") String location
    ) {
        /*
         * 创建日历是写操作，必须让用户确认，避免 Agent 误创建日程。
         * 后续真实接入时，应在适配器层处理时区、重复事件和冲突检测。
         */
        Map<String, Object> request = ConnectorResponseFactory.orderedRequest(
                "providerCode", providerCode,
                "title", title,
                "startTime", startTime,
                "location", location
        );
        return ConnectorResponseFactory.preview(
                CREATE_TOOL_NAME,
                "calendar",
                providerCode,
                ConnectorSideEffectType.WRITE,
                true,
                "已生成面试日历创建预览。真实创建前需要用户确认。",
                request,
                Map.of()
        );
    }
}
