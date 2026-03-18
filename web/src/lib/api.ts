/**
 * 小毛豆 API 客户端
 *
 * 所有与 MaoDouHub 的通信都通过这个模块。
 * 不包含任何业务逻辑。
 */

const API_BASE = import.meta.env.VITE_API_BASE || "";

let _token: string | null = localStorage.getItem("hub_token");

export function setToken(token: string | null) {
  _token = token;
  if (token) {
    localStorage.setItem("hub_token", token);
  } else {
    localStorage.removeItem("hub_token");
  }
}

export function getToken(): string | null {
  return _token;
}

export function isLoggedIn(): boolean {
  return !!_token;
}

async function request(url: string, init?: RequestInit): Promise<any> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string> || {}),
  };
  if (_token) {
    headers["Authorization"] = `Bearer ${_token}`;
  }

  const res = await fetch(API_BASE + url, { ...init, headers });
  const data = await res.json();
  if (!res.ok && res.status === 401) {
    // Token 过期，清除登录状态
    setToken(null);
    window.location.reload();
  }
  return data;
}

// ─── 认证 ─────────────────────────────────────────────

export async function login(userId: string, password: string) {
  return request("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ userId, password }),
  });
}

export async function register(userId: string, userName: string, password: string, department?: string) {
  return request("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ userId, userName, password, department }),
  });
}

// ─── 聊天 ─────────────────────────────────────────────

export async function sendMessage(content: string, attachmentFileIds?: string[]) {
  return request("/api/chat/send", {
    method: "POST",
    body: JSON.stringify({ content, channel: "work", attachmentFileIds }),
  });
}

export async function getMessages(before?: string, limit = 20) {
  const params = new URLSearchParams({ channel: "work", limit: String(limit) });
  if (before) params.set("before", before);
  return request(`/api/chat/messages?${params}`);
}

export async function pollMessages(sinceMessageId?: string) {
  const params = new URLSearchParams({ channel: "work" });
  if (sinceMessageId) params.set("since", sinceMessageId);
  return request(`/api/chat/poll?${params}`);
}

// ─── 状态 ─────────────────────────────────────────────

export async function getPcStatus() {
  return request("/api/status/pc");
}

export async function getMyStatus() {
  return request("/api/status/me");
}

// ─── 文件 ─────────────────────────────────────────────

export async function uploadFile(file: File): Promise<any> {
  const formData = new FormData();
  formData.append("file", file);

  const headers: Record<string, string> = {};
  if (_token) headers["Authorization"] = `Bearer ${_token}`;

  const res = await fetch(API_BASE + "/api/files/upload", {
    method: "POST",
    headers,
    body: formData,
  });
  return res.json();
}

export function getFileDownloadUrl(fileId: string): string {
  return `${API_BASE}/api/files/${fileId}/download`;
}
