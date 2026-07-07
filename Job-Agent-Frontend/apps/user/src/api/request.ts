import type { ApiResult } from "./types";

const TOKEN_NAME_KEY = "job-agent-token-name";
const TOKEN_VALUE_KEY = "job-agent-token-value";

interface BusinessErrorData {
  errorCode?: string;
  message?: string;
}

export class ApiRequestError extends Error {
  code?: number;
  errorCode?: string;
  data?: unknown;

  constructor(message: string, code?: number, errorCode?: string, data?: unknown) {
    super(message);
    this.name = "ApiRequestError";
    this.code = code;
    this.errorCode = errorCode;
    this.data = data;
  }
}

export function saveToken(tokenName: string, tokenValue: string) {
  localStorage.setItem(TOKEN_NAME_KEY, tokenName);
  localStorage.setItem(TOKEN_VALUE_KEY, tokenValue);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_NAME_KEY);
  localStorage.removeItem(TOKEN_VALUE_KEY);
}

export function getToken() {
  return {
    tokenName: localStorage.getItem(TOKEN_NAME_KEY) || "satoken",
    tokenValue: localStorage.getItem(TOKEN_VALUE_KEY) || ""
  };
}

async function readJson<T>(response: Response, url: string, method: string): Promise<T | null> {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text) as T;
  } catch (error) {
    console.error("[Job-Agent] 接口响应不是合法 JSON", {
      url,
      method,
      status: response.status,
      responseText: text,
      error
    });
    throw new Error("接口响应格式错误");
  }
}

function printApiError<T>(title: string, url: string, method: string, response: Response, result: ApiResult<T> | null) {
  console.error(`[Job-Agent] ${title}`, {
    url,
    method,
    httpStatus: response.status,
    code: result?.code,
    message: result?.message,
    data: result?.data
  });
}

function extractBusinessErrorCode(data: unknown) {
  if (!data || typeof data !== "object") {
    return undefined;
  }
  return (data as BusinessErrorData).errorCode;
}

export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const { tokenName, tokenValue } = getToken();
  const headers = new Headers(options.headers);
  const method = options.method || "GET";

  if (!headers.has("Content-Type") && options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  // Sa-Token 默认从 token-name 对应的请求头读取 token。
  if (tokenValue) {
    headers.set(tokenName, tokenValue);
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers
    });

    const result = await readJson<ApiResult<T>>(response, url, method);

    if (!response.ok) {
      printApiError("HTTP 请求失败", url, method, response, result);
      throw new ApiRequestError(
        result?.message || `HTTP ${response.status}`,
        result?.code,
        extractBusinessErrorCode(result?.data),
        result?.data
      );
    }

    if (!result) {
      console.error("[Job-Agent] 接口没有返回响应体", { url, method, httpStatus: response.status });
      throw new Error("接口没有返回响应体");
    }

    // 后端 ResultCodeEnum.SUCCESS 是 200。非 200 统一抛错给页面处理，并输出到浏览器控制台。
    if (result.code !== 200) {
      printApiError("业务请求失败", url, method, response, result);
      throw new ApiRequestError(
        result.message || "请求失败",
        result.code,
        extractBusinessErrorCode(result.data),
        result.data
      );
    }

    return result.data;
  } catch (error) {
    console.error("[Job-Agent] 请求执行异常", {
      url,
      method,
      error
    });
    throw error;
  }
}
