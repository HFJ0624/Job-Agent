import type { ApiResult } from "./types";

const TOKEN_NAME_KEY = "job-agent-admin-token-name";
const TOKEN_VALUE_KEY = "job-agent-admin-token-value";

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
    console.error("[Job-Agent Admin] 接口响应不是合法 JSON", {
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
  console.error(`[Job-Agent Admin] ${title}`, {
    url,
    method,
    httpStatus: response.status,
    code: result?.code,
    message: result?.message,
    data: result?.data
  });
}

export async function request<T>(url: string, options: RequestInit = {}) {
  const { tokenName, tokenValue } = getToken();
  const headers = new Headers(options.headers);
  const method = options.method || "GET";

  if (!headers.has("Content-Type") && options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  // 后台和用户端共用 Sa-Token：登录成功后，后端会告诉前端请求头名称和值。
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
      throw new Error(result?.message || `HTTP ${response.status}`);
    }

    if (!result) {
      console.error("[Job-Agent Admin] 接口没有返回响应体", { url, method, httpStatus: response.status });
      throw new Error("接口没有返回响应体");
    }

    // 后端统一 Result.code=200 表示成功，其它 code 直接交给页面显示错误。
    if (result.code !== 200) {
      printApiError("业务请求失败", url, method, response, result);
      throw new Error(result.message || "请求失败");
    }
    return result.data;
  } catch (error) {
    console.error("[Job-Agent Admin] 请求执行异常", {
      url,
      method,
      error
    });
    throw error;
  }
}
