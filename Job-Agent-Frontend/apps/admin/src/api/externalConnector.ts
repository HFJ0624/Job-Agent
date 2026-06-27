import { request } from "./request";
import type { AgentToolSchemaInfo } from "./types";

export interface ExternalConnectorPreviewPayload {
  toolName: string;
  params: Record<string, unknown>;
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
