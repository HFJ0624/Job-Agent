import { request } from "./request";
import type { AgentToolSchemaInfo } from "./types";

export interface ExternalConnectorPreviewPayload {
  toolName: string;
  params: Record<string, unknown>;
}

/** 一次 MCP 协议交换（含真实 JSON-RPC 帧，任务 G6）。 */
export interface McpExchangeInfo {
  method: string;
  requestJson: string;
  responseJson: string;
  result?: unknown;
  tools?: unknown[];
  toolName?: string;
  text?: string;
}

export function listExternalConnectorTools() {
  return request<AgentToolSchemaInfo[]>("/admin/agent/connectors/tools");
}

export function getExternalConnectorTool(toolName: string) {
  return request<AgentToolSchemaInfo>(`/admin/agent/connectors/tools/${encodeURIComponent(toolName)}`);
}

export function previewExternalConnectorTool(payload: ExternalConnectorPreviewPayload) {
  return request<string>("/admin/agent/connectors/tools/preview", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * MCP 握手（initialize）：展示真实协议帧与服务端信息。
 */
export function initializeMcp() {
  return request<McpExchangeInfo>("/admin/mcp/initialize", { method: "POST" });
}

/**
 * 列出 MCP 服务端工具（tools/list）。
 */
export function listMcpTools() {
  return request<McpExchangeInfo>("/admin/mcp/tools");
}

/**
 * 通过 MCP 协议调用连接器工具（tools/call）。
 */
export function callMcpTool(payload: { toolName: string; arguments: Record<string, unknown> }) {
  return request<McpExchangeInfo>("/admin/mcp/call", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
