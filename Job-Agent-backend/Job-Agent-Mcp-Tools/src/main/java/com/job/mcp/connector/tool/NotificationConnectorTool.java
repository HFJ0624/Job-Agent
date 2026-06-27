package com.job.mcp.connector.tool;

import com.job.mcp.connector.model.ConnectorSideEffectType;
import com.job.mcp.connector.support.ConnectorResponseFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通知渠道连接器工具。
 * 支持站内信、邮件、短信、企业微信、钉钉等通知渠道的统一入口。
 */
@Component
public class NotificationConnectorTool {

    private static final String SEND_TOOL_NAME = "NotificationConnectorTool.sendNotification";

    @Tool("发送通知消息。第一版只返回通知发送预览，不真正触达外部渠道。")
    public String sendNotification(
            @P("通知渠道，例如 in_app、email、sms、wechat_work、dingtalk") String channel,
            @P("接收人标识，例如 userId、邮箱、手机号、外部账号") String receiver,
            @P("通知标题") String title,
            @P("通知内容") String content
    ) {
        /*
         * 通知会触达用户或外部系统，属于有副作用操作。
         * 第一版统一要求确认，第二版可按渠道细分：站内信可低风险，短信/企业微信高风险。
         */
        Map<String, Object> request = ConnectorResponseFactory.orderedRequest(
                "channel", channel,
                "receiver", receiver,
                "title", title,
                "content", content
        );
        return ConnectorResponseFactory.preview(
                SEND_TOOL_NAME,
                "notification",
                channel,
                ConnectorSideEffectType.WRITE,
                true,
                "已生成通知发送预览。真实发送前需要用户确认。",
                request,
                Map.of()
        );
    }
}
