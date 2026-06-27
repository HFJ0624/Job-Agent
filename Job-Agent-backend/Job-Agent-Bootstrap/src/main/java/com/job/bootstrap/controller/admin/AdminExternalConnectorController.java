package com.job.bootstrap.controller.admin;

import com.job.bootstrap.service.AdminExternalConnectorService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台外部连接器管理接口。
 * 第一版提供连接器工具清单、详情和预览调用，不管理真实第三方账号凭证。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent/connectors")
public class AdminExternalConnectorController {

    private final AdminExternalConnectorService adminExternalConnectorService;

    @GetMapping("/tools")
    public Result<List<AgentToolSchema>> listTools() {
        return Result.build(adminExternalConnectorService.listConnectorTools(), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/tools/{toolName}")
    public Result<AgentToolSchema> detail(@PathVariable String toolName) {
        return Result.build(adminExternalConnectorService.getConnectorTool(toolName), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/tools/preview")
    public Result<String> preview(@RequestBody ConnectorPreviewRequest request) {
        return Result.build(
                adminExternalConnectorService.preview(request.getToolName(), request.getParams()),
                ResultCodeEnum.SUCCESS
        );
    }

    @Data
    public static class ConnectorPreviewRequest {
        private String toolName;
        private Map<String, Object> params;
    }
}
