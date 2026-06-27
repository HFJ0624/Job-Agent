package com.job.mcp.connector.tool;

import com.job.mcp.connector.model.ConnectorSideEffectType;
import com.job.mcp.connector.support.ConnectorResponseFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 邮箱连接器工具。
 * 第一版提供读邮件和发邮件的标准工具入口，真实 IMAP/SMTP/OAuth 接入放到后续适配器。
 */
@Component
public class EmailConnectorTool {

    private static final String READ_TOOL_NAME = "EmailConnectorTool.readEmails";
    private static final String SEND_TOOL_NAME = "EmailConnectorTool.sendEmail";

    @Tool("读取邮箱中的求职相关邮件。第一版只返回读取预览，不真正连接邮箱。")
    public String readEmails(
            @P("邮箱渠道编码，例如 qq-mail、gmail、outlook") String providerCode,
            @P("搜索关键词，例如 面试、offer、笔试") String keyword,
            @P("最多读取数量") Integer limit
    ) {
        Map<String, Object> request = ConnectorResponseFactory.orderedRequest(
                "providerCode", providerCode,
                "keyword", keyword,
                "limit", limit == null ? 10 : limit
        );
        Map<String, Object> data = ConnectorResponseFactory.orderedRequest(
                "emails", java.util.List.of(),
                "nextStep", "配置邮箱授权后，可读取邮件并抽取面试邀约"
        );
        return ConnectorResponseFactory.preview(
                READ_TOOL_NAME,
                "email",
                providerCode,
                ConnectorSideEffectType.READ,
                false,
                "已生成邮箱读取预览，第一版未连接真实邮箱。",
                request,
                data
        );
    }

    @Tool("发送求职沟通邮件。发送类操作有副作用，真实执行前必须经过用户确认。")
    public String sendEmail(
            @P("邮箱渠道编码，例如 qq-mail、gmail、outlook") String providerCode,
            @P("收件人邮箱") String to,
            @P("邮件标题") String subject,
            @P("邮件正文") String content
    ) {
        /*
         * 发送邮件会影响外部世界，所以第一版只返回预览，并显式要求用户确认。
         * 后续真实适配器执行前，也应再次校验确认状态和收件人白名单/风险策略。
         */
        Map<String, Object> request = ConnectorResponseFactory.orderedRequest(
                "providerCode", providerCode,
                "to", to,
                "subject", subject,
                "content", content
        );
        return ConnectorResponseFactory.preview(
                SEND_TOOL_NAME,
                "email",
                providerCode,
                ConnectorSideEffectType.WRITE,
                true,
                "已生成邮件发送预览。真实发送前需要用户确认。",
                request,
                Map.of()
        );
    }
}
